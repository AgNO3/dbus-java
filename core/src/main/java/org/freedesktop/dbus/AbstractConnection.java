/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.FatalDBusException;
import org.freedesktop.dbus.exceptions.FatalException;
import org.freedesktop.dbus.exceptions.NotConnected;


/**
 * Handles a connection to DBus.
 */
public abstract class AbstractConnection {

    static final Logger log = Logger.getLogger(AbstractConnection.class);

    protected class FallbackContainer {

        private Map<String[], ExportedObject> fallbacks = new HashMap<>();


        public synchronized void add ( String path, ExportedObject eo ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Adding fallback on " + path + " of " + eo);
            }
            this.fallbacks.put(path.split("/"), eo);
        }


        public synchronized void remove ( String path ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Removing fallback on " + path);
            }
            this.fallbacks.remove(path.split("/"));
        }


        public synchronized ExportedObject get ( String path ) {
            int best = 0;
            int i = 0;
            ExportedObject bestobject = null;
            String[] pathel = path.split("/");
            for ( String[] fbpath : this.fallbacks.keySet() ) {
                if ( log.isTraceEnabled() ) {
                    log.trace("Trying fallback path " + Arrays.deepToString(fbpath) + " to match " + Arrays.deepToString(pathel));
                }
                for ( i = 0; i < pathel.length && i < fbpath.length; i++ )
                    if ( !pathel[ i ].equals(fbpath[ i ]) )
                        break;
                if ( i > 0 && i == fbpath.length && i > best )
                    bestobject = this.fallbacks.get(fbpath);

                if ( log.isTraceEnabled() ) {
                    log.trace("Trying fallback path " + "Matches " + i + " bestobject now " + bestobject);
                }
            }

            if ( log.isDebugEnabled() ) {
                log.debug("Found fallback for " + path + " of " + bestobject);
            }
            return bestobject;
        }
    }

    protected class _thread extends Thread {

        public _thread () {
            setName("DBusConnection");
        }


        @Override
        public void run () {
            try {
                Message m = null;
                while ( AbstractConnection.this._run ) {
                    m = null;

                    // read from the wire
                    try {
                        // this blocks on outgoing being non-empty or a message being available.
                        m = readIncoming();
                        if ( m != null ) {

                            if ( log.isDebugEnabled() ) {
                                log.debug("Got Incoming Message: " + m);
                            }

                            synchronized ( this ) {
                                notifyAll();
                            }

                            if ( m instanceof DBusSignal )
                                handleMessage((DBusSignal) m);
                            else if ( m instanceof MethodCall )
                                handleMessage((MethodCall) m);
                            else if ( m instanceof MethodReturn )
                                handleMessage((MethodReturn) m);
                            else if ( m instanceof Error )
                                handleMessage((Error) m);

                            m = null;
                        }
                    }
                    catch ( NotConnected e ) {
                        log.info("Connection is disconnected", e);
                    }
                    catch ( Exception e ) {
                        log.debug("Exception for incoming message:", e);
                        if ( e instanceof FatalException ) {
                            disconnect();
                        }
                    }

                }
                synchronized ( this ) {
                    notifyAll();
                }
            }
            catch ( Exception e ) {
                log.warn("Uncaught Exception:", e);
            }
        }
    }

    private class _globalhandler implements org.freedesktop.dbus.DBus.Peer, org.freedesktop.dbus.DBus.Introspectable {

        private String objectpath;


        public _globalhandler () {
            this.objectpath = null;
        }


        public _globalhandler ( String objectpath ) {
            this.objectpath = objectpath;
        }


        @Override
        public boolean isRemote () {
            return false;
        }


        @Override
        public void Ping () {
            return;
        }


        @Override
        public String Introspect () {
            String intro = AbstractConnection.this.objectTree.Introspect(this.objectpath);
            if ( null == intro ) {
                ExportedObject eo = AbstractConnection.this.fallbackcontainer.get(this.objectpath);
                if ( null != eo )
                    intro = eo.introspectiondata;
            }
            if ( null == intro )
                throw new DBus.Error.UnknownObject("Introspecting on non-existant object");

            return "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\" "
                    + "\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n" + intro;
        }
    }

    protected class _workerthread extends Thread {

        private boolean runWorkerThread = true;


        /**
         * 
         */
        public _workerthread () {
            super("Worker");
        }


        public void halt () {
            this.runWorkerThread = false;
        }


        @Override
        public void run () {
            while ( this.runWorkerThread ) {
                Runnable r = null;
                synchronized ( AbstractConnection.this.runnables ) {
                    while ( AbstractConnection.this.runnables.size() == 0 && this.runWorkerThread )
                        try {
                            AbstractConnection.this.runnables.wait();
                        }
                        catch ( InterruptedException Ie ) {}
                    if ( AbstractConnection.this.runnables.size() > 0 )
                        r = AbstractConnection.this.runnables.removeFirst();
                }
                if ( null != r )
                    r.run();
            }
        }
    }

    private class _sender extends Thread {

        public _sender () {
            setName("Sender");
        }


        @Override
        public void run () {
            Message m = null;

            log.info("Monitoring outbound queue");
            // block on the outbound queue and send from it
            while ( AbstractConnection.this._run ) {
                if ( null != AbstractConnection.this.outgoing )
                    synchronized ( AbstractConnection.this.outgoing ) {
                        log.trace("Blocking");
                        while ( AbstractConnection.this.outgoing.size() == 0 && AbstractConnection.this._run )
                            try {
                                AbstractConnection.this.outgoing.wait();
                            }
                            catch ( InterruptedException Ie ) {}
                        log.trace("Notified");
                        if ( AbstractConnection.this.outgoing.size() > 0 )
                            m = AbstractConnection.this.outgoing.remove();
                        log.debug("Got message: " + m);
                    }
                if ( null != m )
                    sendMessage(m);
                m = null;
            }

            log.info("Flushing outbound queue and quitting");
            // flush the outbound queue before disconnect.
            if ( null != AbstractConnection.this.outgoing )
                do {
                    EfficientQueue ogq = AbstractConnection.this.outgoing;
                    synchronized ( ogq ) {
                        AbstractConnection.this.outgoing = null;
                    }
                    if ( !ogq.isEmpty() )
                        m = ogq.remove();
                    else
                        m = null;

                    try {
                        sendMessage(m);
                    }
                    catch ( NotConnected e ) {
                        log.warn("Failed to send message " + m, e);
                    }
                }
                while ( null != m );

            // close the underlying streams
        }
    }

    /**
     * Timeout in us on checking the BUS for incoming messages and sending outgoing messages
     */
    protected static final int TIMEOUT = 100000;
    /** Initial size of the pending calls map */
    private static final int PENDING_MAP_INITIAL_SIZE = 10;
    static final String BUSNAME_REGEX = "^[-_a-zA-Z][-_a-zA-Z0-9]*(\\.[-_a-zA-Z][-_a-zA-Z0-9]*)*$";
    static final String CONNID_REGEX = "^:[0-9]*\\.[0-9]*$";
    static final String OBJECT_REGEX = "^/([-_a-zA-Z0-9]+(/[-_a-zA-Z0-9]+)*)?$";
    static final byte THREADCOUNT = 4;
    static final int MAX_ARRAY_LENGTH = 67108864;
    static final int MAX_NAME_LENGTH = 255;
    protected Map<String, ExportedObject> exportedObjects;
    ObjectTree objectTree;
    private _globalhandler _globalhandlerreference;
    protected Map<DBusInterface, RemoteObject> importedObjects;
    protected Map<SignalTuple, Vector<DBusSigHandler<? extends DBusSignal>>> handledSignals;
    protected EfficientMap pendingCalls;
    protected Map<MethodCall, CallbackHandler<Object>> pendingCallbacks;
    protected Map<MethodCall, DBusAsyncReply<Object>> pendingCallbackReplys;
    protected LinkedList<Runnable> runnables;
    protected LinkedList<_workerthread> workers;
    protected FallbackContainer fallbackcontainer;
    protected boolean _run;
    EfficientQueue outgoing;
    LinkedList<Error> pendingErrors;
    static final Map<Thread, DBusCallInfo> infomap = new HashMap<>();
    protected _thread thread;
    protected _sender sender;
    protected Transport transport;
    protected String addr;
    protected boolean weakreferences = false;
    static final Pattern dollar_pattern = Pattern.compile("[$]");
    static final boolean FLOAT_SUPPORT;
    protected boolean connected = false;
    private ClassLoader userClassLoader;

    static {
        FLOAT_SUPPORT = ( null != System.getenv("DBUS_JAVA_FLOATS") );
    }


    protected AbstractConnection ( String address, ClassLoader cl ) throws DBusException {
        this.userClassLoader = cl;
        this.exportedObjects = new HashMap<>();
        this.importedObjects = new HashMap<>();
        this._globalhandlerreference = new _globalhandler();
        synchronized ( this.exportedObjects ) {
            this.exportedObjects.put(null, new ExportedObject(this._globalhandlerreference, this.weakreferences));
        }
        this.handledSignals = new HashMap<>();
        this.pendingCalls = new EfficientMap(PENDING_MAP_INITIAL_SIZE);
        this.outgoing = new EfficientQueue(PENDING_MAP_INITIAL_SIZE);
        this.pendingCallbacks = new HashMap<>();
        this.pendingCallbackReplys = new HashMap<>();
        this.pendingErrors = new LinkedList<>();
        this.runnables = new LinkedList<>();
        this.workers = new LinkedList<>();
        this.objectTree = new ObjectTree();
        this.fallbackcontainer = new FallbackContainer();
        synchronized ( this.workers ) {
            for ( int i = 0; i < THREADCOUNT; i++ ) {
                _workerthread t = new _workerthread();
                t.start();
                this.workers.add(t);
            }
        }
        this._run = true;
        this.addr = address;
    }


    protected void listen () {
        // start listening
        this.thread = new _thread();
        this.thread.start();
        this.sender = new _sender();
        this.sender.start();
    }


    /**
     * Change the number of worker threads to receive method calls and handle signals.
     * Default is 4 threads
     * 
     * @param newcount
     *            The new number of worker Threads to use.
     */
    public void changeThreadCount ( byte newcount ) {
        synchronized ( this.workers ) {
            if ( this.workers.size() > newcount ) {
                int n = this.workers.size() - newcount;
                for ( int i = 0; i < n; i++ ) {
                    _workerthread t = this.workers.removeFirst();
                    t.halt();
                }
            }
            else if ( this.workers.size() < newcount ) {
                int n = newcount - this.workers.size();
                for ( int i = 0; i < n; i++ ) {
                    _workerthread t = new _workerthread();
                    t.start();
                    this.workers.add(t);
                }
            }
        }
    }


    private void addRunnable ( Runnable r ) {
        synchronized ( this.runnables ) {
            this.runnables.add(r);
            this.runnables.notifyAll();
        }
    }


    String getExportedObject ( DBusInterface i ) throws DBusException {
        synchronized ( this.exportedObjects ) {
            for ( String s : this.exportedObjects.keySet() ) {
                Object o = this.exportedObjects.get(s).object.get();
                if ( o != null && i.equals(o) )
                    return s;
            }
        }

        String s = this.importedObjects.get(i).objectpath;
        if ( null != s )
            return s;

        throw new DBusException("Not an object exported or imported by this connection");
    }


    abstract DBusInterface getExportedObject ( String source, String path ) throws DBusException;


    /**
     * Returns a structure with information on the current method call.
     * 
     * @return the DBusCallInfo for this method call, or null if we are not in a method call.
     */
    public static DBusCallInfo getCallInfo () {
        DBusCallInfo info;
        synchronized ( infomap ) {
            info = infomap.get(Thread.currentThread());
        }
        return info;
    }


    /**
     * If set to true the bus will not hold a strong reference to exported objects.
     * If they go out of scope they will automatically be unexported from the bus.
     * The default is to hold a strong reference, which means objects must be
     * explicitly unexported before they will be garbage collected.
     */
    public void setWeakReferences ( boolean weakreferences ) {
        this.weakreferences = weakreferences;
    }


    /**
     * Export an object so that its methods can be called on DBus.
     * 
     * @param objectpath
     *            The path to the object we are exposing. MUST be in slash-notation, like "/org/freedesktop/Local",
     *            and SHOULD end with a capitalised term. Only one object may be exposed on each path at any one time,
     *            but an object
     *            may be exposed on several paths at once.
     * @param object
     *            The object to export.
     * @throws DBusException
     *             If the objectpath is already exporting an object.
     *             or if objectpath is incorrectly formatted,
     */
    public void exportObject ( String objectpath, DBusInterface object ) throws DBusException {
        if ( null == objectpath || "".equals(objectpath) )
            throw new DBusException("Must Specify an Object Path");
        if ( !objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectpath);
        synchronized ( this.exportedObjects ) {
            if ( null != this.exportedObjects.get(objectpath) )
                throw new DBusException("Object already exported");
            ExportedObject eo = new ExportedObject(object, this.weakreferences);
            this.exportedObjects.put(objectpath, eo);
            this.objectTree.add(objectpath, eo, eo.introspectiondata);
        }
    }


    /**
     * Export an object as a fallback object.
     * This object will have it's methods invoked for all paths starting
     * with this object path.
     * 
     * @param objectprefix
     *            The path below which the fallback handles calls.
     *            MUST be in slash-notation, like "/org/freedesktop/Local",
     * @param object
     *            The object to export.
     * @throws DBusException
     *             If the objectpath is incorrectly formatted,
     */
    public void addFallback ( String objectprefix, DBusInterface object ) throws DBusException {
        if ( null == objectprefix || "".equals(objectprefix) )
            throw new DBusException("Must Specify an Object Path");
        if ( !objectprefix.matches(OBJECT_REGEX) || objectprefix.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectprefix);
        ExportedObject eo = new ExportedObject(object, this.weakreferences);
        this.fallbackcontainer.add(objectprefix, eo);
    }


    /**
     * Remove a fallback
     * 
     * @param objectprefix
     *            The prefix to remove the fallback for.
     */
    public void removeFallback ( String objectprefix ) {
        this.fallbackcontainer.remove(objectprefix);
    }


    /**
     * Stop Exporting an object
     * 
     * @param objectpath
     *            The objectpath to stop exporting.
     */
    public void unExportObject ( String objectpath ) {
        synchronized ( this.exportedObjects ) {
            this.exportedObjects.remove(objectpath);
            this.objectTree.remove(objectpath);
        }
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
     * @return A reference to a remote object.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusInterface
     * @throws DBusException
     *             If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    /**
     * Send a signal.
     * 
     * @param signal
     *            The signal to send.
     */
    public void sendSignal ( DBusSignal signal ) {
        queueOutgoing(signal);
    }


    void queueOutgoing ( Message m ) {
        synchronized ( this.outgoing ) {
            if ( null == this.outgoing )
                return;
            this.outgoing.add(m);
            log.debug("Notifying outgoing thread");
            this.outgoing.notifyAll();
        }
    }


    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     * 
     * @param type
     *            The signal to watch for.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler ( Class<T> type, DBusSigHandler<T> handler ) throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        removeSigHandler(new DBusMatchRule(type), handler);
    }


    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     * 
     * @param type
     *            The signal to watch for.
     * @param object
     *            The object emitting the signal.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler ( Class<T> type, DBusInterface object, DBusSigHandler<T> handler ) throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        String objectpath = this.importedObjects.get(object).objectpath;
        if ( !objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectpath);
        removeSigHandler(new DBusMatchRule(type, null, objectpath), handler);
    }


    protected abstract <T extends DBusSignal> void removeSigHandler ( DBusMatchRule rule, DBusSigHandler<T> handler ) throws DBusException;


    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type and name.
     * 
     * @param type
     *            The signal to watch for.
     * @param handler
     *            The handler to call when a signal is received.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void addSigHandler ( Class<T> type, DBusSigHandler<T> handler ) throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        addSigHandler(new DBusMatchRule(type), (DBusSigHandler<? extends DBusSignal>) handler);
    }


    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type, name and object.
     * 
     * @param type
     *            The signal to watch for.
     * @param object
     *            The object from which the signal will be emitted
     * @param handler
     *            The handler to call when a signal is received.
     * @throws DBusException
     *             If listening for the signal on the bus failed.
     * @throws ClassCastException
     *             If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void addSigHandler ( Class<T> type, DBusInterface object, DBusSigHandler<T> handler ) throws DBusException {
        if ( !DBusSignal.class.isAssignableFrom(type) )
            throw new ClassCastException("Not A DBus Signal");
        String objectpath = this.importedObjects.get(object).objectpath;
        if ( !objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH )
            throw new DBusException("Invalid object path: " + objectpath);
        addSigHandler(new DBusMatchRule(type, null, objectpath), (DBusSigHandler<? extends DBusSignal>) handler);
    }


    protected abstract <T extends DBusSignal> void addSigHandler ( DBusMatchRule rule, DBusSigHandler<T> handler ) throws DBusException;


    protected <T extends DBusSignal> void addSigHandlerWithoutMatch ( Class<? extends DBusSignal> signal, DBusSigHandler<T> handler )
            throws DBusException {
        DBusMatchRule rule = new DBusMatchRule(signal);
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
     */
    public void disconnect () {
        this.connected = false;
        log.info("Sending disconnected signal");
        try {
            handleMessage(new org.freedesktop.dbus.DBus.Local.Disconnected("/"));
        }
        catch ( Exception ee ) {
            log.warn("Failed to handle disconnect message", ee);
        }

        log.info("Disconnecting Abstract Connection");

        // run all pending tasks.
        while ( this.runnables.size() > 0 )
            synchronized ( this.runnables ) {
                this.runnables.notifyAll();
            }

        // stop the main thread
        this._run = false;

        // unblock the sending thread.
        synchronized ( this.outgoing ) {
            this.outgoing.notifyAll();
        }

        // disconnect from the trasport layer
        try {
            if ( null != this.transport ) {
                this.transport.disconnect();
                this.transport = null;
            }
        }
        catch ( IOException IOe ) {
            log.error("Error in disconnect", IOe);
        }

        // stop all the workers
        synchronized ( this.workers ) {
            for ( _workerthread t : this.workers )
                t.halt();
        }

        // make sure none are blocking on the runnables queue still
        synchronized ( this.runnables ) {
            this.runnables.notifyAll();
        }
    }


    @Override
    public void finalize () {
        disconnect();
    }


    /**
     * Return any DBus error which has been received.
     * 
     * @return A DBusExecutionException, or null if no error is pending.
     */
    public DBusExecutionException getError () {
        synchronized ( this.pendingErrors ) {
            if ( this.pendingErrors.size() == 0 )
                return null;

            return this.pendingErrors.removeFirst().getException(this);
        }
    }


    /**
     * Call a method asynchronously and set a callback.
     * This handler will be called in a separate thread.
     * 
     * @param object
     *            The remote object on which to call the method.
     * @param m
     *            The name of the method on the interface to call.
     * @param callback
     *            The callback handler.
     * @param parameters
     *            The parameters to call the method with.
     */
    @SuppressWarnings ( "unchecked" )
    public <A> void callWithCallback ( DBusInterface object, String m, CallbackHandler<A> callback, Object... parameters ) {
        if ( log.isTraceEnabled() ) {
            log.trace("callWithCallback(" + object + "," + m + ", " + callback);
        }
        Class<?>[] types = new Class[parameters.length];
        for ( int i = 0; i < parameters.length; i++ )
            types[ i ] = parameters[ i ].getClass();
        RemoteObject ro = this.importedObjects.get(object);

        try {
            Method me;
            if ( null == ro.iface )
                me = object.getClass().getMethod(m, types);
            else
                me = ro.iface.getMethod(m, types);
            RemoteInvocationHandler.executeRemoteMethod(
                ro,
                me,
                this,
                RemoteInvocationHandler.CALL_TYPE_CALLBACK,
                (CallbackHandler<Object>) callback,
                parameters);
        }
        catch ( DBusExecutionException DBEe ) {
            log.warn("Failed to call (callback)", DBEe);
            throw DBEe;
        }
        catch ( Exception e ) {
            log.warn("Failed to call (callback)", e);
            throw new DBusExecutionException(e.getMessage());
        }
    }


    /**
     * Call a method asynchronously and get a handle with which to get the reply.
     * 
     * @param object
     *            The remote object on which to call the method.
     * @param m
     *            The name of the method on the interface to call.
     * @param parameters
     *            The parameters to call the method with.
     * @return A handle to the call.
     */
    public DBusAsyncReply<?> callMethodAsync ( DBusInterface object, String m, Object... parameters ) {
        Class<?>[] types = new Class[parameters.length];
        for ( int i = 0; i < parameters.length; i++ )
            types[ i ] = parameters[ i ].getClass();
        RemoteObject ro = this.importedObjects.get(object);

        try {
            Method me;
            if ( null == ro.iface )
                me = object.getClass().getMethod(m, types);
            else
                me = ro.iface.getMethod(m, types);
            return (DBusAsyncReply<?>) RemoteInvocationHandler.executeRemoteMethod(
                ro,
                me,
                this,
                RemoteInvocationHandler.CALL_TYPE_ASYNC,
                null,
                parameters);
        }
        catch ( DBusExecutionException DBEe ) {
            log.warn("Failed to call (async)", DBEe);
            throw DBEe;
        }
        catch ( Exception e ) {
            log.warn("Failed to call (async)", e);
            throw new DBusExecutionException(e.getMessage());
        }
    }


    void handleMessage ( final MethodCall m ) {
        if ( log.isDebugEnabled() ) {
            log.debug("Handling incoming method call: " + m);
        }

        ExportedObject eo = null;
        Method meth = null;
        Object o = null;

        if ( null == m.getInterface() || m.getInterface().equals("org.freedesktop.DBus.Peer")
                || m.getInterface().equals("org.freedesktop.DBus.Introspectable") ) {
            synchronized ( this.exportedObjects ) {
                eo = this.exportedObjects.get(null);
            }
            if ( null != eo && null == eo.object.get() ) {
                unExportObject(null);
                eo = null;
            }
            if ( null != eo ) {
                meth = eo.methods.get(new MethodTuple(m.getName(), m.getSig()));
            }
            if ( null != meth )
                o = new _globalhandler(m.getPath());
            else
                eo = null;
        }
        if ( null == o ) {
            // now check for specific exported functions

            synchronized ( this.exportedObjects ) {
                eo = this.exportedObjects.get(m.getPath());
            }
            if ( null != eo && null == eo.object.get() ) {
                log.info("Unexporting " + m.getPath() + " implicitly");
                unExportObject(m.getPath());
                eo = null;
            }

            if ( null == eo ) {
                eo = this.fallbackcontainer.get(m.getPath());
            }

            if ( null == eo ) {
                try {
                    queueOutgoing(new Error(m, new DBus.Error.UnknownObject(m.getPath() + " is not an object provided by this process.")));
                }
                catch ( DBusException DBe ) {}
                return;
            }
            if ( log.isTraceEnabled() ) {
                log.trace("Searching for method " + m.getName() + " with signature " + m.getSig());
                log.trace("List of methods on " + eo + ":");
                for ( MethodTuple mt : eo.methods.keySet() )
                    log.trace("   " + mt + " => " + eo.methods.get(mt));
            }
            meth = eo.methods.get(new MethodTuple(m.getName(), m.getSig()));
            if ( null == meth ) {
                try {
                    queueOutgoing(new Error(m, new DBus.Error.UnknownMethod(String.format(
                        "The method `%s.%s' does not exist on this object.",
                        m.getInterface(),
                        m.getName()))));
                }
                catch ( DBusException DBe ) {}
                return;
            }
            o = eo.object.get();
        }

        // now execute it
        final Method me = meth;
        final Object ob = o;
        final boolean noreply = ( 1 == ( m.getFlags() & Message.Flags.NO_REPLY_EXPECTED ) );
        final DBusCallInfo info = new DBusCallInfo(m);
        final AbstractConnection conn = this;
        if ( log.isDebugEnabled() ) {
            log.debug("Adding Runnable for method " + meth);
        }
        addRunnable(new Runnable() {

            private boolean run = false;


            @Override
            public synchronized void run () {
                if ( this.run )
                    return;
                this.run = true;
                if ( log.isDebugEnabled() ) {
                    log.debug("Running method " + me + " for remote call");
                }
                try {
                    if ( me == null ) {
                        log.warn("me == null");
                        return;
                    }
                    Type[] ts = me.getGenericParameterTypes();
                    m.setArgs(Marshalling.deSerializeParameters(m.getParameters(), ts, conn));
                    if ( log.isTraceEnabled() ) {
                        log.trace("Deserialised " + Arrays.deepToString(m.getParameters()) + " to types " + Arrays.deepToString(ts));
                    }
                }
                catch ( Exception e ) {
                    log.warn("Failed to deserialize method params", e);
                    try {
                        conn.queueOutgoing(new Error(m, new DBus.Error.UnknownMethod("Failure in de-serializing message: " + e)));
                    }
                    catch ( DBusException DBe ) {}
                    return;
                }

                try {
                    synchronized ( infomap ) {
                        infomap.put(Thread.currentThread(), info);
                    }
                    Object result;
                    try {
                        if ( log.isTraceEnabled() ) {
                            log.trace("Invoking Method: " + me + " on " + ob + " with parameters " + Arrays.deepToString(m.getParameters()));
                        }
                        result = me.invoke(ob, m.getParameters());
                    }
                    catch ( InvocationTargetException ITe ) {
                        log.warn("Failed to invoke method", ITe);
                        throw ITe.getCause();
                    }
                    synchronized ( infomap ) {
                        infomap.remove(Thread.currentThread());
                    }
                    if ( !noreply ) {
                        MethodReturn reply;
                        if ( Void.TYPE.equals(me.getReturnType()) )
                            reply = new MethodReturn(m, null);
                        else {
                            StringBuffer sb = new StringBuffer();
                            for ( String s : Marshalling.getDBusType(me.getGenericReturnType()) )
                                sb.append(s);
                            Object[] nr = Marshalling.convertParameters(new Object[] {
                                result
                            }, new Type[] {
                                me.getGenericReturnType()
                            }, conn);

                            reply = new MethodReturn(m, sb.toString(), nr);
                        }
                        conn.queueOutgoing(reply);
                    }
                }
                catch ( DBusExecutionException DBEe ) {
                    log.info("Failed to call method, producing error", DBEe);
                    try {
                        conn.queueOutgoing(new Error(m, DBEe));
                    }
                    catch ( DBusException DBe ) {}
                }
                catch ( Throwable e ) {
                    log.warn("Failed to call method", e);
                    try {
                        conn.queueOutgoing(new Error(m, new DBusExecutionException(String.format(
                            "Error Executing Method %s.%s: %s",
                            m.getInterface(),
                            m.getName(),
                            e.getMessage()))));
                    }
                    catch ( DBusException DBe ) {}
                }
            }
        });
    }


    @SuppressWarnings ( {
        "unchecked"
    } )
    void handleMessage ( final DBusSignal s ) {
        if ( log.isDebugEnabled() ) {
            log.debug("Handling incoming signal: " + s);
        }
        Vector<DBusSigHandler<? extends DBusSignal>> v = new Vector<>();
        synchronized ( this.handledSignals ) {
            Vector<DBusSigHandler<? extends DBusSignal>> t;
            t = this.handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), null, null));
            if ( null != t )
                v.addAll(t);
            t = this.handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), s.getPath(), null));
            if ( null != t )
                v.addAll(t);
            t = this.handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), null, s.getSource()));
            if ( null != t )
                v.addAll(t);
            t = this.handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), s.getPath(), s.getSource()));
            if ( null != t )
                v.addAll(t);
        }
        if ( 0 == v.size() )
            return;
        final AbstractConnection conn = this;
        for ( final DBusSigHandler<? extends DBusSignal> h : v ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Adding Runnable for signal " + s + " with handler " + h);
            }
            addRunnable(new Runnable() {

                private boolean run = false;


                @Override
                public synchronized void run () {
                    if ( this.run )
                        return;
                    this.run = true;
                    try {
                        DBusSignal rs;
                        if ( s instanceof DBusSignal.internalsig || s.getClass().equals(DBusSignal.class) )
                            rs = s.createReal(conn);
                        else
                            rs = s;
                        ( (DBusSigHandler<DBusSignal>) h ).handle(rs);
                    }
                    catch ( DBusException DBe ) {
                        log.warn("Error handling signal", DBe);
                        try {
                            conn.queueOutgoing(new Error(s, new DBusExecutionException("Error handling signal " + s.getInterface() + "."
                                    + s.getName() + ": " + DBe.getMessage())));
                        }
                        catch ( DBusException DBe2 ) {}
                    }
                }
            });
        }
    }


    void handleMessage ( final Error err ) {
        if ( log.isDebugEnabled() ) {
            log.debug("Handling incoming error: " + err);
        }
        MethodCall m = null;
        if ( null == this.pendingCalls )
            return;
        synchronized ( this.pendingCalls ) {
            if ( this.pendingCalls.contains(err.getReplySerial()) )
                m = this.pendingCalls.remove(err.getReplySerial());
        }
        if ( null != m ) {
            m.setReply(err);
            CallbackHandler<?> cbh = null;
            synchronized ( this.pendingCallbacks ) {
                cbh = this.pendingCallbacks.remove(m);
                if ( log.isTraceEnabled() ) {
                    log.trace(cbh + " = pendingCallbacks.remove(" + m + ")");
                }
                this.pendingCallbackReplys.remove(m);
            }
            // queue callback for execution
            if ( null != cbh ) {
                final CallbackHandler<?> fcbh = cbh;
                if ( log.isTraceEnabled() ) {
                    log.trace("Adding Error Runnable with callback handler " + fcbh);
                }
                addRunnable(new Runnable() {

                    private boolean run = false;


                    @Override
                    public synchronized void run () {
                        if ( this.run )
                            return;
                        this.run = true;
                        try {
                            if ( log.isTraceEnabled() ) {
                                log.trace("Running Error Callback for " + err);
                            }
                            DBusCallInfo info = new DBusCallInfo(err);
                            synchronized ( infomap ) {
                                infomap.put(Thread.currentThread(), info);
                            }

                            fcbh.handleError(err.getException(AbstractConnection.this));
                            synchronized ( infomap ) {
                                infomap.remove(Thread.currentThread());
                            }

                        }
                        catch ( Exception e ) {
                            log.warn("Failed to run error callback", e);
                        }
                    }
                });
            }

        }
        else
            synchronized ( this.pendingErrors ) {
                this.pendingErrors.addLast(err);
            }
    }


    void handleMessage ( final MethodReturn mr ) {
        if ( log.isDebugEnabled() ) {
            log.debug("Handling incoming method return: " + mr);
        }
        MethodCall m = null;
        if ( null == this.pendingCalls )
            return;
        synchronized ( this.pendingCalls ) {
            if ( this.pendingCalls.contains(mr.getReplySerial()) )
                m = this.pendingCalls.remove(mr.getReplySerial());
        }
        if ( null != m ) {
            m.setReply(mr);
            mr.setCall(m);
            CallbackHandler<Object> cbh = null;
            DBusAsyncReply<?> asr = null;
            synchronized ( this.pendingCallbacks ) {
                cbh = this.pendingCallbacks.remove(m);
                if ( log.isTraceEnabled() ) {
                    log.trace(cbh + " = pendingCallbacks.remove(" + m + ")");
                }
                asr = this.pendingCallbackReplys.remove(m);
            }
            // queue callback for execution
            if ( null != cbh ) {
                final CallbackHandler<Object> fcbh = cbh;
                final DBusAsyncReply<?> fasr = asr;
                if ( log.isTraceEnabled() ) {
                    log.trace("Adding Runnable for method " + fasr.getMethod() + " with callback handler " + fcbh);
                }
                addRunnable(new Runnable() {

                    private boolean run = false;


                    @Override
                    public synchronized void run () {
                        if ( this.run )
                            return;
                        this.run = true;
                        try {
                            if ( log.isTraceEnabled() ) {
                                log.trace("Running Callback for " + mr);
                            }
                            DBusCallInfo info = new DBusCallInfo(mr);
                            synchronized ( infomap ) {
                                infomap.put(Thread.currentThread(), info);
                            }

                            fcbh.handle(RemoteInvocationHandler.convertRV(mr.getSig(), mr.getParameters(), fasr.getMethod(), fasr.getConnection()));
                            synchronized ( infomap ) {
                                infomap.remove(Thread.currentThread());
                            }

                        }
                        catch ( Exception e ) {
                            log.warn("Failed to run callback", e);
                        }
                    }
                });
            }

        }
        else
            try {
                queueOutgoing(new Error(mr, new DBusExecutionException("Spurious reply. No message with the given serial id was awaiting a reply.")));
            }
            catch ( DBusException DBe ) {}
    }


    protected void sendMessage ( Message m ) {
        try {
            if ( !this.connected )
                throw new NotConnected("Disconnected");
            if ( m instanceof DBusSignal )
                ( (DBusSignal) m ).appendbody(this);

            if ( m instanceof MethodCall ) {
                if ( 0 == ( m.getFlags() & Message.Flags.NO_REPLY_EXPECTED ) )
                    if ( null == this.pendingCalls )
                        ( (MethodCall) m ).setReply(new Error(
                            "org.freedesktop.DBus.Local",
                            "org.freedesktop.DBus.Local.Disconnected",
                            0,
                            "s",
                            new Object[] {
                                "Disconnected"
                            }));
                    else
                        synchronized ( this.pendingCalls ) {
                            this.pendingCalls.put(m.getSerial(), (MethodCall) m);
                        }
            }

            this.transport.mout.writeMessage(m);

        }
        catch ( Exception e ) {
            log.debug("Failed to send message", e);
            if ( m instanceof MethodCall && e instanceof NotConnected )
                try {
                    ( (MethodCall) m ).setReply(new Error(
                        "org.freedesktop.DBus.Local",
                        "org.freedesktop.DBus.Local.Disconnected",
                        0,
                        "s",
                        new Object[] {
                            "Disconnected"
                        }));
                }
                catch ( DBusException DBe ) {}
            if ( m instanceof MethodCall && e instanceof DBusExecutionException )
                try {
                    ( (MethodCall) m ).setReply(new Error(m, e));
                }
                catch ( DBusException DBe ) {}
            else if ( m instanceof MethodCall )
                try {
                    log.info("Setting reply to " + m + " as an error");
                    ( (MethodCall) m ).setReply(new Error(m, new DBusExecutionException("Message Failed to Send: " + e.getMessage())));
                }
                catch ( DBusException DBe ) {}
            else if ( m instanceof MethodReturn )
                try {
                    this.transport.mout.writeMessage(new Error(m, e));
                }
                catch ( IOException IOe ) {
                    log.warn("Failed to write return message", IOe);
                }
                catch ( DBusException IOe ) {
                    log.warn("Failed to write return message", e);
                }
            if ( e instanceof IOException )
                disconnect();
        }
    }


    Message readIncoming () throws DBusException {
        if ( !this.connected )
            throw new NotConnected("No transport present");
        Message m = null;
        try {
            m = this.transport.min.readMessage();
        }
        catch ( IOException IOe ) {
            throw new FatalDBusException(IOe.getMessage());
        }
        return m;
    }


    /**
     * Returns the address this connection is connected to.
     */
    public BusAddress getAddress () throws ParseException {
        return new BusAddress(this.addr);
    }


    public Class<?> loadClass ( String className ) throws ClassNotFoundException {
        ClassLoader userCL = this.getUserClassLoader();

        if ( userCL != null ) {
            try {
                return userCL.loadClass(className);
            }
            catch ( ClassNotFoundException e ) {}
        }

        return this.getClass().getClassLoader().loadClass(className);
    }


    /**
     * @return
     */
    public ClassLoader getUserClassLoader () {

        if ( this.userClassLoader == null ) {
            return Thread.currentThread().getContextClassLoader();
        }

        return this.userClassLoader;
    }
}
