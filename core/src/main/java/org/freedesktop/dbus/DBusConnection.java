/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.NotConnected;
import org.freedesktop.dbus.types.UInt32;


/**
 * Handles a connection to DBus.
 * <p>
 * This is a Singleton class, only 1 connection to the SYSTEM or SESSION busses can be made. Repeated calls to
 * getConnection will return the same reference.
 * </p>
 * <p>
 * Signal Handlers and method calls from remote objects are run in their own threads, you MUST handle the concurrency
 * issues.
 * </p>
 */
public class DBusConnection extends AbstractConnection implements AutoCloseable {

    @SuppressWarnings ( "hiding" )
    static final Logger log = Logger.getLogger(DBusConnection.class);

    /**
     * Add addresses of peers to a set which will watch for them to
     * disappear and automatically remove them from the set.
     */
    public class PeerSet implements Set<String>, DBusSigHandler<DBus.NameOwnerChanged> {

        private Set<String> addresses;


        public PeerSet () {
            this.addresses = new TreeSet<>();
            try {
                addSigHandler(new DBusMatchRule(DBus.NameOwnerChanged.class, null, null), this);
            }
            catch ( DBusException DBe ) {
                log.warn("Failed to add signal handler", DBe);
            }
        }


        @Override
        public void handle ( DBus.NameOwnerChanged noc ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Received NameOwnerChanged(" + noc.name + "," + noc.old_owner + "," + noc.new_owner + ")");
            }
            if ( "".equals(noc.new_owner) && this.addresses.contains(noc.name) )
                remove(noc.name);
        }


        @Override
        public boolean add ( String address ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Adding " + address);
            }
            synchronized ( this.addresses ) {
                return this.addresses.add(address);
            }
        }


        @Override
        public boolean addAll ( Collection<? extends String> addrs ) {
            synchronized ( this.addresses ) {
                return this.addresses.addAll(addrs);
            }
        }


        @Override
        public void clear () {
            synchronized ( this.addresses ) {
                this.addresses.clear();
            }
        }


        @Override
        public boolean contains ( Object o ) {
            return this.addresses.contains(o);
        }


        @Override
        public boolean containsAll ( Collection<?> os ) {
            return this.addresses.containsAll(os);
        }


        @Override
        public boolean equals ( Object o ) {
            if ( o instanceof PeerSet ) {
                return ( (PeerSet) o ).addresses.equals(this.addresses);
            }
            return false;
        }


        @Override
        public int hashCode () {
            return this.addresses.hashCode();
        }


        @Override
        public boolean isEmpty () {
            return this.addresses.isEmpty();
        }


        @Override
        public Iterator<String> iterator () {
            return this.addresses.iterator();
        }


        @Override
        public boolean remove ( Object o ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Removing " + o);
            }
            synchronized ( this.addresses ) {
                return this.addresses.remove(o);
            }
        }


        @Override
        public boolean removeAll ( Collection<?> os ) {
            synchronized ( this.addresses ) {
                return this.addresses.removeAll(os);
            }
        }


        @Override
        public boolean retainAll ( Collection<?> os ) {
            synchronized ( this.addresses ) {
                return this.addresses.retainAll(os);
            }
        }


        @Override
        public int size () {
            return this.addresses.size();
        }


        @Override
        public Object[] toArray () {
            synchronized ( this.addresses ) {
                return this.addresses.toArray();
            }
        }


        @Override
        public <T> T[] toArray ( T[] a ) {
            synchronized ( this.addresses ) {
                return this.addresses.toArray(a);
            }
        }
    }

    private class _sighandler implements DBusSigHandler<DBusSignal> {

        /**
         * 
         */
        public _sighandler () {}


        @Override
        public void handle ( DBusSignal s ) {
            if ( s instanceof org.freedesktop.dbus.DBus.Local.Disconnected ) {
                log.info("Handling Disconnected signal from bus");
                try {
                    Error err = new Error("org.freedesktop.DBus.Local", "org.freedesktop.DBus.Local.Disconnected", 0, "s", new Object[] {
                        "Disconnected"
                    });
                    if ( null != DBusConnection.this.pendingCalls )
                        synchronized ( DBusConnection.this.pendingCalls ) {
                            long[] set = DBusConnection.this.pendingCalls.getKeys();
                            for ( long l : set )
                                if ( -1 != l ) {
                                    MethodCall m = DBusConnection.this.pendingCalls.remove(l);
                                    if ( null != m )
                                        m.setReply(err);
                                }
                        }
                    synchronized ( DBusConnection.this.pendingErrors ) {
                        DBusConnection.this.pendingErrors.add(err);
                    }
                }
                catch ( DBusException DBe ) {
                    log.debug("Failed to handle disconnect signlar", DBe);
                }
            }
            else if ( s instanceof org.freedesktop.dbus.DBus.NameAcquired ) {
                DBusConnection.this.busnames.add( ( (org.freedesktop.dbus.DBus.NameAcquired) s ).name);
            }
        }
    }

    /**
     * System Bus
     */
    public static final int SYSTEM = 0;
    /**
     * Session Bus
     */
    public static final int SESSION = 1;

    public static final String DEFAULT_SYSTEM_BUS_ADDRESS = "unix:path=/var/run/dbus/system_bus_socket";

    List<String> busnames;

    private static final Map<Object, DBusConnection> conn = new HashMap<>();
    private int _refcount = 0;
    private Object _reflock = new Object();
    private DBus _dbus;


    /**
     * Connect to the BUS. If a connection already exists to the specified Bus, a reference to it is returned.
     * 
     * @param address
     *            The address of the bus to connect to
     * @throws DBusException
     *             If there is a problem connecting to the Bus.
     */
    public static DBusConnection getConnection ( String address ) throws DBusException {
        return getConnection(address, null);
    }


    public static DBusConnection getConnection ( String address, ClassLoader cl ) throws DBusException {
        synchronized ( conn ) {
            DBusConnection c = conn.get(address);
            if ( null != c ) {
                synchronized ( c._reflock ) {
                    c._refcount++;
                }
                return c;
            }

            c = new DBusConnection(address, cl);
            conn.put(address, c);
            return c;

        }
    }


    /**
     * Connect to the BUS. If a connection already exists to the specified Bus, a reference to it is returned.
     * 
     * @param bustype
     *            The Bus to connect to.
     * @see #SYSTEM
     * @see #SESSION
     * @throws DBusException
     *             If there is a problem connecting to the Bus.
     */
    public static DBusConnection getConnection ( int bustype ) throws DBusException {
        return getConnection(bustype, null);
    }


    public static DBusConnection getConnection ( int bustype, ClassLoader cl ) throws DBusException {
        synchronized ( conn ) {
            String s = null;
            switch ( bustype ) {
            case SYSTEM:
                s = System.getenv("DBUS_SYSTEM_BUS_ADDRESS");
                if ( null == s )
                    s = DEFAULT_SYSTEM_BUS_ADDRESS;
                break;
            case SESSION:
                s = System.getenv("DBUS_SESSION_BUS_ADDRESS");
                if ( null == s ) {
                    // address gets stashed in $HOME/.dbus/session-bus/`dbus-uuidgen --get`-`sed 's/:\(.\)\..*/\1/' <<<
                    // $DISPLAY`
                    String display = System.getenv("DISPLAY");
                    if ( null == display )
                        throw new DBusException("Cannot Resolve Session Bus Address");
                    File uuidfile = new File("/var/lib/dbus/machine-id");
                    if ( !uuidfile.exists() )
                        throw new DBusException("Cannot Resolve Session Bus Address");

                    try {
                        String uuid;
                        try ( BufferedReader ruuidfile = new BufferedReader(new FileReader(uuidfile)) ) {
                            uuid = ruuidfile.readLine();
                        }

                        String homedir = System.getProperty("user.home");
                        File addressfile = new File(homedir + "/.dbus/session-bus", uuid + "-" + display.replaceAll(":([0-9]*)\\..*", "$1"));
                        if ( !addressfile.exists() ) {
                            throw new DBusException("Cannot Resolve Session Bus Address");
                        }

                        try ( BufferedReader raddressfile = new BufferedReader(new FileReader(addressfile)) ) {
                            String l;
                            while ( null != ( l = raddressfile.readLine() ) ) {
                                if ( log.isTraceEnabled() ) {
                                    log.trace("Reading D-Bus session data: " + l);
                                }
                                if ( l.matches("DBUS_SESSION_BUS_ADDRESS.*") ) {
                                    s = l.replaceAll("^[^=]*=", "");
                                    if ( log.isTraceEnabled() ) {
                                        log.trace("Parsing " + l + " to " + s);
                                    }
                                }
                            }
                            if ( null == s || "".equals(s) )
                                throw new DBusException("Cannot Resolve Session Bus Address");
                            log.info("Read bus address " + s + " from file " + addressfile.toString());
                        }
                    }
                    catch ( Exception e ) {
                        throw new DBusException("Cannot Resolve Session Bus Address", e);
                    }
                }
                break;
            default:
                throw new DBusException("Invalid Bus Type: " + bustype);
            }
            DBusConnection c = conn.get(s);
            if ( log.isTraceEnabled() ) {
                log.trace("Getting bus connection for " + s + ": " + c);
            }
            if ( null != c ) {
                synchronized ( c._reflock ) {
                    c._refcount++;
                }
                return c;
            }

            if ( log.isDebugEnabled() ) {
                log.debug("Creating new bus connection to: " + s);
            }
            c = new DBusConnection(s, cl);
            conn.put(s, c);
            return c;
        }
    }


    private DBusConnection ( String address, ClassLoader cl ) throws DBusException {
        super(address, cl);
        this.busnames = new Vector<>();

        synchronized ( this._reflock ) {
            this._refcount = 1;
        }

        try {
            this.transport = new Transport(this.addr, AbstractConnection.TIMEOUT);
            this.connected = true;
        }
        catch ( IOException IOe ) {
            disconnect();
            throw new DBusException("Failed to connect to bus " + IOe.getMessage(), IOe);
        }
        catch ( ParseException Pe ) {
            disconnect();
            throw new DBusException("Failed to connect to bus " + Pe.getMessage(), Pe);
        }

        // start listening for calls
        listen();

        // register disconnect handlers
        DBusSigHandler<?> h = new _sighandler();
        addSigHandlerWithoutMatch(org.freedesktop.dbus.DBus.Local.Disconnected.class, h);
        addSigHandlerWithoutMatch(org.freedesktop.dbus.DBus.NameAcquired.class, h);

        // register ourselves
        this._dbus = getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        try {
            this.busnames.add(this._dbus.Hello());
        }
        catch ( DBusExecutionException DBEe ) {
            throw new DBusException(DBEe.getMessage(), DBEe);
        }
    }


    DBusInterface dynamicProxy ( String source, String path ) throws DBusException {
        log.info("Introspecting " + path + " on " + source + " for dynamic proxy creation");
        try {
            DBus.Introspectable intro = getRemoteObject(source, path, DBus.Introspectable.class);
            String data = intro.Introspect();
            if ( log.isTraceEnabled() ) {
                log.trace("Got introspection data: " + data);
            }
            String[] tags = data.split("[<>]");
            Vector<String> ifaces = new Vector<>();
            for ( String tag : tags ) {
                if ( tag.startsWith("interface") ) {
                    String ifName = tag.replaceAll("^interface *name *= *['\"]([^'\"]*)['\"].*$", "$1");
                    if ( ifName.startsWith("org.freedesktop.DBus") ) {
                        ifName = ifName.replace("org.freedesktop.DBus", "org.freedesktop.dbus.DBus");
                    }
                    ifaces.add(ifName);
                }
            }
            Vector<Class<? extends Object>> ifcs = new Vector<>();
            for ( String iface : ifaces ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Trying interface " + iface);
                }
                int j = 0;
                while ( j >= 0 ) {
                    try {
                        Class<?> ifclass = this.loadClass(iface);
                        if ( !ifcs.contains(ifclass) )
                            ifcs.add(ifclass);
                        break;
                    }
                    catch ( Exception e ) {}
                    j = iface.lastIndexOf(".");
                    char[] cs = iface.toCharArray();
                    if ( j >= 0 ) {
                        cs[ j ] = '$';
                        iface = String.valueOf(cs);
                    }
                }
            }

            if ( ifcs.size() == 0 )
                throw new DBusException(String.format("Could not find an interface to cast to have [%s]", ifaces));

            RemoteObject ro = new RemoteObject(source, path, null, false);
            DBusInterface newi = (DBusInterface) Proxy.newProxyInstance(
                this.getUserClassLoader(),
                ifcs.toArray(new Class[0]),
                new RemoteInvocationHandler(this, ro));
            this.importedObjects.put(newi, ro);
            return newi;
        }
        catch ( DBusExecutionException e ) {
            throw e;
        }
        catch ( Exception e ) {
            throw new DBusException(String.format("Failed to create proxy object for %s exported by %s. Reason: %s", path, source, e.getMessage()), e);
        }
    }


    @Override
    DBusInterface getExportedObject ( String source, String path ) throws DBusException {
        ExportedObject o = null;
        synchronized ( this.exportedObjects ) {
            o = this.exportedObjects.get(path);
        }
        if ( null != o && null == o.object.get() ) {
            unExportObject(path);
            o = null;
        }
        if ( null != o )
            return o.object.get();
        if ( null == source )
            throw new DBusException("Not an object exported by this connection and no remote specified");
        return dynamicProxy(source, path);
    }


    /**
     * Release a bus name.
     * Releases the name so that other people can use it
     * 
     * @param busname
     *            The name to release. MUST be in dot-notation like "org.freedesktop.local"
     * @throws DBusException
     *             If the busname is incorrectly formatted.
     */
    public void releaseBusName ( String busname ) throws DBusException {
        if ( !busname.matches(BUSNAME_REGEX) || busname.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name");
        synchronized ( this.busnames ) {
            try {
                this._dbus.ReleaseName(busname);
            }
            catch ( DBusExecutionException DBEe ) {
                log.warn(DBEe);
                throw new DBusException(DBEe.getMessage());
            }
            this.busnames.remove(busname);
        }
    }


    /**
     * Request a bus name.
     * Request the well known name that this should respond to on the Bus.
     * 
     * @param busname
     *            The name to respond to. MUST be in dot-notation like "org.freedesktop.local"
     * @throws DBusException
     *             If the register name failed, or our name already exists on the bus.
     *             or if busname is incorrectly formatted.
     */
    public void requestBusName ( String busname ) throws DBusException {
        if ( !busname.matches(BUSNAME_REGEX) || busname.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name");
        synchronized ( this.busnames ) {
            UInt32 rv;
            try {
                rv = this._dbus.RequestName(busname, new UInt32(DBus.DBUS_NAME_FLAG_REPLACE_EXISTING | DBus.DBUS_NAME_FLAG_DO_NOT_QUEUE));
            }
            catch ( DBusExecutionException DBEe ) {
                throw new DBusException(DBEe.getMessage(), DBEe);
            }
            switch ( rv.intValue() ) {
            case DBus.DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER:
                break;
            case DBus.DBUS_REQUEST_NAME_REPLY_IN_QUEUE:
                throw new DBusException("Failed to register bus name");
            case DBus.DBUS_REQUEST_NAME_REPLY_EXISTS:
                throw new DBusException("Failed to register bus name");
            case DBus.DBUS_REQUEST_NAME_REPLY_ALREADY_OWNER:
                break;
            default:
                break;
            }
            this.busnames.add(busname);
        }
    }


    /**
     * Returns the unique name of this connection.
     */
    public String getUniqueName () {
        return this.busnames.get(0);
    }


    /**
     * Returns all the names owned by this connection.
     */
    public String[] getNames () {
        Set<String> names = new TreeSet<>();
        names.addAll(this.busnames);
        return names.toArray(new String[0]);
    }


    public <I extends DBusInterface> I getPeerRemoteObject ( String busname, String objectpath, Class<I> type ) throws DBusException {
        return getPeerRemoteObject(busname, objectpath, type, true);
    }


    /**
     * Return a reference to a remote object.
     * This method will resolve the well known name (if given) to a unique bus name when you call it.
     * This means that if a well known name is released by one process and acquired by another calls to
     * objects gained from this method will continue to operate on the original process.
     * 
     * This method will use bus introspection to determine the interfaces on a remote object and so
     * <b>may block</b> and <b>may fail</b>. The resulting proxy object will, however, be castable
     * to any interface it implements. It will also autostart the process if applicable. Also note
     * that the resulting proxy may fail to execute the correct method with overloaded methods
     * and that complex types may fail in interesting ways. Basically, if something odd happens,
     * try specifying the interface explicitly.
     * 
     * @param busname
     *            The bus name to connect to. Usually a well known bus name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath
     *            The path on which the process is exporting the object.$
     * @return A reference to a remote object.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusInterface
     * @throws DBusException
     *             If busname or objectpath are incorrectly formatted.
     */
    public DBusInterface getPeerRemoteObject ( String busname, String objectpath ) throws DBusException {
        if ( null == busname )
            throw new DBusException("Invalid bus name: null");

        if ( ( !busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) ) || busname.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + busname);

        String unique = this._dbus.GetNameOwner(busname);

        return dynamicProxy(unique, objectpath);
    }


    /**
     * Return a reference to a remote object.
     * This method will always refer to the well known name (if given) rather than resolving it to a unique bus name.
     * In particular this means that if a process providing the well known name disappears and is taken over by another
     * process
     * proxy objects gained by this method will make calls on the new proccess.
     * 
     * This method will use bus introspection to determine the interfaces on a remote object and so
     * <b>may block</b> and <b>may fail</b>. The resulting proxy object will, however, be castable
     * to any interface it implements. It will also autostart the process if applicable. Also note
     * that the resulting proxy may fail to execute the correct method with overloaded methods
     * and that complex types may fail in interesting ways. Basically, if something odd happens,
     * try specifying the interface explicitly.
     * 
     * @param busname
     *            The bus name to connect to. Usually a well known bus name name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath
     *            The path on which the process is exporting the object.
     * @return A reference to a remote object.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusInterface
     * @throws DBusException
     *             If busname or objectpath are incorrectly formatted.
     */
    public DBusInterface getRemoteObject ( String busname, String objectpath ) throws DBusException {
        if ( null == busname )
            throw new DBusException("Invalid bus name: null");
        if ( null == objectpath )
            throw new DBusException("Invalid object path: null");

        if ( ( !busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) ) || busname.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + busname);

        if ( !objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectpath);

        return dynamicProxy(busname, objectpath);
    }


    /**
     * Return a reference to a remote object.
     * This method will resolve the well known name (if given) to a unique bus name when you call it.
     * This means that if a well known name is released by one process and acquired by another calls to
     * objects gained from this method will continue to operate on the original process.
     * 
     * @param busname
     *            The bus name to connect to. Usually a well known bus name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath
     *            The path on which the process is exporting the object.$
     * @param type
     *            The interface they are exporting it on. This type must have the same full class name and exposed
     *            method signatures
     *            as the interface the remote object is exporting.
     * @param autostart
     *            Disable/Enable auto-starting of services in response to calls on this object.
     *            Default is enabled; when calling a method with auto-start enabled, if the destination is a well-known
     *            name
     *            and is not owned the bus will attempt to start a process to take the name. When disabled an error is
     *            returned immediately.
     * @return A reference to a remote object.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusInterface
     * @throws DBusException
     *             If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    public <I extends DBusInterface> I getPeerRemoteObject ( String busname, String objectpath, Class<I> type, boolean autostart )
            throws DBusException {
        if ( null == busname )
            throw new DBusException("Invalid bus name: null");

        if ( ( !busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) ) || busname.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + busname);

        String unique = this._dbus.GetNameOwner(busname);

        return getRemoteObject(unique, objectpath, type, autostart);
    }


    /**
     * Return a reference to a remote object.
     * This method will always refer to the well known name (if given) rather than resolving it to a unique bus name.
     * In particular this means that if a process providing the well known name disappears and is taken over by another
     * process
     * proxy objects gained by this method will make calls on the new proccess.
     * 
     * @param busname
     *            The bus name to connect to. Usually a well known bus name name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath
     *            The path on which the process is exporting the object.
     * @param type
     *            The interface they are exporting it on. This type must have the same full class name and exposed
     *            method signatures
     *            as the interface the remote object is exporting.
     * @return A reference to a remote object.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusInterface
     * @throws DBusException
     *             If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    public <I extends DBusInterface> I getRemoteObject ( String busname, String objectpath, Class<I> type ) throws DBusException {
        return getRemoteObject(busname, objectpath, type, true);
    }


    /**
     * Return a reference to a remote object.
     * This method will always refer to the well known name (if given) rather than resolving it to a unique bus name.
     * In particular this means that if a process providing the well known name disappears and is taken over by another
     * process
     * proxy objects gained by this method will make calls on the new proccess.
     * 
     * @param busname
     *            The bus name to connect to. Usually a well known bus name name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath
     *            The path on which the process is exporting the object.
     * @param type
     *            The interface they are exporting it on. This type must have the same full class name and exposed
     *            method signatures
     *            as the interface the remote object is exporting.
     * @param autostart
     *            Disable/Enable auto-starting of services in response to calls on this object.
     *            Default is enabled; when calling a method with auto-start enabled, if the destination is a well-known
     *            name
     *            and is not owned the bus will attempt to start a process to take the name. When disabled an error is
     *            returned immediately.
     * @return A reference to a remote object.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusInterface
     * @throws DBusException
     *             If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    public <I extends DBusInterface> I getRemoteObject ( String busname, String objectpath, Class<I> type, boolean autostart ) throws DBusException {
        if ( null == busname )
            throw new DBusException("Invalid bus name: null");
        if ( null == objectpath )
            throw new DBusException("Invalid object path: null");
        if ( null == type )
            throw new ClassCastException("Not A DBus Interface");

        if ( ( !busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) ) || busname.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + busname);

        if ( !objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectpath);

        if ( !DBusInterface.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Interface");

        // don't let people import things which don't have a
        // valid D-Bus interface name
        if ( type.getName().equals(type.getSimpleName()) )
            throw new DBusException("DBusInterfaces cannot be declared outside a package");

        RemoteObject ro = new RemoteObject(busname, objectpath, type, autostart);
        @SuppressWarnings ( "unchecked" )
        I i = (I) Proxy.newProxyInstance(type.getClassLoader(), new Class[] {
            type
        }, new RemoteInvocationHandler(this, ro));
        this.importedObjects.put(i, ro);
        return i;
    }


    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     * 
     * @param type
     *            The signal to watch for.
     * @param source
     *            The source of the signal.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler ( Class<T> type, String source, DBusSigHandler<T> handler ) throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        if ( source.matches(BUSNAME_REGEX) )
            throw new DBusException("Cannot watch for signals based on well known bus name as source, only unique names.");
        if ( !source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + source);
        removeSigHandler(new DBusMatchRule(type, source, null), handler);
    }


    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     * 
     * @param type
     *            The signal to watch for.
     * @param source
     *            The source of the signal.
     * @param object
     *            The object emitting the signal.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler ( Class<T> type, String source, DBusInterface object, DBusSigHandler<T> handler )
            throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        if ( source.matches(BUSNAME_REGEX) )
            throw new DBusException("Cannot watch for signals based on well known bus name as source, only unique names.");
        if ( !source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + source);
        String objectpath = this.importedObjects.get(object).objectpath;
        if ( !objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectpath);
        removeSigHandler(new DBusMatchRule(type, source, objectpath), handler);
    }


    @Override
    protected <T extends DBusSignal> void removeSigHandler ( DBusMatchRule rule, DBusSigHandler<T> handler ) throws DBusException {

        SignalTuple key = new SignalTuple(rule.getInterface(), rule.getMember(), rule.getObject(), rule.getSource());
        synchronized ( this.handledSignals ) {
            Vector<DBusSigHandler<? extends DBusSignal>> v = this.handledSignals.get(key);
            if ( null != v ) {
                v.remove(handler);
                if ( 0 == v.size() ) {
                    this.handledSignals.remove(key);
                    try {
                        this._dbus.RemoveMatch(rule.toString());
                    }
                    catch ( NotConnected NC ) {
                        log.warn("Not connected", NC);
                    }
                    catch ( DBusExecutionException DBEe ) {
                        throw new DBusException(DBEe.getMessage(), DBEe);
                    }
                }
            }
        }
    }


    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type, name and source.
     * 
     * @param type
     *            The signal to watch for.
     * @param source
     *            The process which will send the signal. This <b>MUST</b> be a unique bus name and not a well known
     *            name.
     * @param handler
     *            The handler to call when a signal is received.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void addSigHandler ( Class<T> type, String source, DBusSigHandler<T> handler ) throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        if ( source.matches(BUSNAME_REGEX) )
            throw new DBusException("Cannot watch for signals based on well known bus name as source, only unique names.");
        if ( !source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + source);
        addSigHandler(new DBusMatchRule(type, source, null), (DBusSigHandler<? extends DBusSignal>) handler);
    }


    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type, name, source and
     * object.
     * 
     * @param type
     *            The signal to watch for.
     * @param source
     *            The process which will send the signal. This <b>MUST</b> be a unique bus name and not a well known
     *            name.
     * @param object
     *            The object from which the signal will be emitted
     * @param handler
     *            The handler to call when a signal is received.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void addSigHandler ( Class<T> type, String source, DBusInterface object, DBusSigHandler<T> handler )
            throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        if ( source.matches(BUSNAME_REGEX) )
            throw new DBusException("Cannot watch for signals based on well known bus name as source, only unique names.");
        if ( !source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid bus name: " + source);
        String objectpath = this.importedObjects.get(object).objectpath;
        if ( !objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectpath);
        addSigHandler(new DBusMatchRule(type, source, objectpath), (DBusSigHandler<? extends DBusSignal>) handler);
    }


    @Override
    protected <T extends DBusSignal> void addSigHandler ( DBusMatchRule rule, DBusSigHandler<T> handler ) throws DBusException {
        try {
            this._dbus.AddMatch(rule.toString());
        }
        catch ( DBusExecutionException DBEe ) {
            throw new DBusException(DBEe.getMessage(), DBEe);
        }
        SignalTuple key = new SignalTuple(rule.getInterface(), rule.getMember(), rule.getObject(), rule.getSource());
        synchronized ( this.handledSignals ) {
            Vector<DBusSigHandler<? extends DBusSignal>> v = this.handledSignals.get(key);
            if ( null == v ) {
                v = new Vector<>();
                v.add(handler);
                this.handledSignals.put(key, v);
            }
            else
                v.add(handler);
        }
    }


    /**
     * Disconnect from the Bus.
     * This only disconnects when the last reference to the bus has disconnect called on it
     * or has been destroyed.
     */
    @Override
    public void disconnect () {
        synchronized ( conn ) {
            synchronized ( this._reflock ) {
                if ( 0 == --this._refcount ) {
                    log.info("Disconnecting DBusConnection");
                    // Set all pending messages to have an error.
                    try {
                        Error err = new Error("org.freedesktop.DBus.Local", "org.freedesktop.DBus.Local.Disconnected", 0, "s", new Object[] {
                            "Disconnected"
                        });
                        synchronized ( this.pendingCalls ) {
                            long[] set = this.pendingCalls.getKeys();
                            for ( long l : set )
                                if ( -1 != l ) {
                                    MethodCall m = this.pendingCalls.remove(l);
                                    if ( null != m )
                                        m.setReply(err);
                                }
                            this.pendingCalls = null;
                        }
                        synchronized ( this.pendingErrors ) {
                            this.pendingErrors.add(err);
                        }
                    }
                    catch ( DBusException DBe ) {
                        log.debug("Failure while disconnecting", DBe);
                    }

                    conn.remove(this.addr);
                    super.disconnect();
                }
            }
        }
    }


    /**
     * {@inheritDoc}
     * 
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close () {
        this.disconnect();
    }
}
