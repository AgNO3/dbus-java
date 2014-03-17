/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.bin.daemon;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.BusAddress;
import org.freedesktop.dbus.DBus;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.DirectConnection;
import org.freedesktop.dbus.Error;
import org.freedesktop.dbus.Marshalling;
import org.freedesktop.dbus.Message;
import org.freedesktop.dbus.MessageReader;
import org.freedesktop.dbus.MessageWriter;
import org.freedesktop.dbus.MethodCall;
import org.freedesktop.dbus.MethodReturn;
import org.freedesktop.dbus.Transport;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.FatalException;
import org.freedesktop.dbus.types.UInt32;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;


/**
 * A replacement DBusDaemon
 */
@SuppressWarnings ( {
    "javadoc", "nls"
} )
public class DBusDaemon extends Thread {

    static final Logger log = Logger.getLogger(DBusDaemon.class);

    public static final int QUEUE_POLL_WAIT = 500;

    static class Connstruct {

        public Socket sock;
        public MessageReader min;
        public MessageWriter mout;
        public String unique;


        public Connstruct ( Socket sock ) throws IOException {
            this.sock = sock;
            this.min = new MessageReader(sock.getInputStream());
            this.mout = new MessageWriter(sock.getOutputStream());
        }


        @Override
        public String toString () {
            return null == this.unique ? ":?-?" : this.unique;
        }
    }

    static class MagicMap <A, B> {

        private Map<A, LinkedList<B>> m;
        private LinkedList<A> q;
        private String name;


        public MagicMap ( String name ) {
            this.m = new HashMap<>();
            this.q = new LinkedList<>();
            this.name = name;
        }


        public A head () {
            return this.q.getFirst();
        }


        public void putFirst ( A a, B b ) {
            if ( log.isDebugEnabled() ) {
                log.debug("<" + this.name + "> Queueing {" + a + " => " + b + "}");
            }
            if ( this.m.containsKey(a) )
                this.m.get(a).add(b);
            else {
                LinkedList<B> l = new LinkedList<>();
                l.add(b);
                this.m.put(a, l);
            }
            this.q.addFirst(a);
        }


        public void putLast ( A a, B b ) {
            if ( log.isDebugEnabled() ) {
                log.debug("<" + this.name + "> Queueing {" + a + " => " + b + "}");
            }
            if ( this.m.containsKey(a) )
                this.m.get(a).add(b);
            else {
                LinkedList<B> l = new LinkedList<>();
                l.add(b);
                this.m.put(a, l);
            }
            this.q.addLast(a);
        }


        public List<B> remove ( A a ) {
            if ( log.isDebugEnabled() ) {
                log.debug("<" + this.name + "> Removing {" + a + "}");
            }
            this.q.remove(a);
            return this.m.remove(a);
        }


        public int size () {
            return this.q.size();
        }
    }

    public class DBusServer extends Thread implements DBus, DBus.Introspectable, DBus.Peer {

        public DBusServer () {
            setName("Server");
        }

        public Connstruct c;
        public Message m;


        @Override
        public boolean isRemote () {
            return false;
        }


        @Override
        public String Hello () {
            synchronized ( this.c ) {
                if ( null != this.c.unique )
                    throw new org.freedesktop.dbus.DBus.Error.AccessDenied("Connection has already sent a Hello message");
                synchronized ( DBusDaemon.this.unique_lock ) {
                    this.c.unique = ":1." + ( ++DBusDaemon.this.next_unique );
                }
            }
            synchronized ( DBusDaemon.this.names ) {
                DBusDaemon.this.names.put(this.c.unique, this.c);
            }

            log.info("Client " + this.c.unique + " registered");

            try {
                send(this.c, new DBusSignal(
                    "org.freedesktop.DBus",
                    "/org/freedesktop/DBus",
                    "org.freedesktop.DBus",
                    "NameAcquired",
                    "s",
                    this.c.unique));
                DBusSignal s = new DBusSignal(
                    "org.freedesktop.DBus",
                    "/org/freedesktop/DBus",
                    "org.freedesktop.DBus",
                    "NameOwnerChanged",
                    "sss",
                    this.c.unique,
                    "",
                    this.c.unique);
                send(null, s);
            }
            catch ( DBusException DBe ) {
                log.warn(DBe);
            }
            return this.c.unique;
        }


        @Override
        public String[] ListNames () {
            String[] ns;
            synchronized ( DBusDaemon.this.names ) {
                Set<String> nss = DBusDaemon.this.names.keySet();
                ns = nss.toArray(new String[0]);
            }
            return ns;
        }


        @Override
        public boolean NameHasOwner ( String name ) {
            boolean rv;
            synchronized ( DBusDaemon.this.names ) {
                rv = DBusDaemon.this.names.containsKey(name);
            }
            return rv;
        }


        @Override
        public String GetNameOwner ( String name ) {
            Connstruct owner = DBusDaemon.this.names.get(name);
            String o;
            if ( null == owner )
                o = "";
            else
                o = owner.unique;
            return o;
        }


        @Override
        public UInt32 GetConnectionUnixUser ( String connection_name ) {
            return new UInt32(0);
        }


        @Override
        public UInt32 StartServiceByName ( String name, UInt32 flags ) {
            return new UInt32(0);
        }


        @Override
        public UInt32 RequestName ( String name, UInt32 flags ) {
            boolean exists = false;
            synchronized ( DBusDaemon.this.names ) {
                if ( ! ( exists = DBusDaemon.this.names.containsKey(name) ) )
                    DBusDaemon.this.names.put(name, this.c);
            }

            int rv;
            if ( exists ) {
                rv = DBus.DBUS_REQUEST_NAME_REPLY_EXISTS;
            }
            else {
                log.info("Client " + this.c.unique + " acquired name " + name);
                rv = DBus.DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER;
                try {
                    send(this.c, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "NameAcquired", "s", name));
                    send(null, new DBusSignal(
                        "org.freedesktop.DBus",
                        "/org/freedesktop/DBus",
                        "org.freedesktop.DBus",
                        "NameOwnerChanged",
                        "sss",
                        name,
                        "",
                        this.c.unique));
                }
                catch ( DBusException DBe ) {
                    log.warn(DBe);
                }
            }

            return new UInt32(rv);
        }


        @Override
        public UInt32 ReleaseName ( String name ) {
            boolean exists = false;
            synchronized ( DBusDaemon.this.names ) {
                exists = DBusDaemon.this.names.containsKey(name) && DBusDaemon.this.names.get(name).equals(this.c);
                if ( exists )
                    DBusDaemon.this.names.remove(name);
            }

            int rv;
            if ( !exists ) {
                rv = DBus.DBUS_RELEASE_NAME_REPLY_NON_EXISTANT;
            }
            else {
                log.info("Client " + this.c.unique + " acquired name " + name);
                rv = DBus.DBUS_RELEASE_NAME_REPLY_RELEASED;
                try {
                    send(this.c, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "NameLost", "s", name));
                    send(null, new DBusSignal(
                        "org.freedesktop.DBus",
                        "/org/freedesktop/DBus",
                        "org.freedesktop.DBus",
                        "NameOwnerChanged",
                        "sss",
                        name,
                        this.c.unique,
                        ""));
                }
                catch ( DBusException DBe ) {
                    log.warn(DBe);
                }
            }
            return new UInt32(rv);
        }


        @Override
        public void AddMatch ( String matchrule ) throws Error.MatchRuleInvalid {
            if ( log.isTraceEnabled() ) {
                log.trace("Adding match rule: " + matchrule);
            }
            synchronized ( DBusDaemon.this.sigrecips ) {
                if ( !DBusDaemon.this.sigrecips.contains(this.c) )
                    DBusDaemon.this.sigrecips.add(this.c);
            }
            return;
        }


        @Override
        public void RemoveMatch ( String matchrule ) throws Error.MatchRuleInvalid {
            if ( log.isTraceEnabled() )
                log.trace("Removing match rule: " + matchrule);
            return;
        }


        @Override
        public String[] ListQueuedOwners ( String name ) {
            return new String[0];
        }


        @Override
        public UInt32 GetConnectionUnixProcessID ( String connection_name ) {
            return new UInt32(0);
        }


        @Override
        public Byte[] GetConnectionSELinuxSecurityContext ( String a ) {
            ;
            return new Byte[0];
        }


        @Override
        public void ReloadConfig () {
            return;
        }


        @SuppressWarnings ( "unchecked" )
        private void handleMessage ( Connstruct cstruct, Message msg ) throws DBusException {
            if ( log.isTraceEnabled() ) {
                log.trace("Handling message " + msg + " from " + cstruct.unique);
            }
            if ( ! ( msg instanceof MethodCall ) )
                return;
            Object[] args = msg.getParameters();

            Class<? extends Object>[] cs = new Class[args.length];

            for ( int i = 0; i < cs.length; i++ )
                cs[ i ] = args[ i ].getClass();

            java.lang.reflect.Method meth = null;
            Object rv = null;

            try {
                meth = DBusServer.class.getMethod(msg.getName(), cs);
                try {
                    this.c = cstruct;
                    this.m = msg;
                    rv = meth.invoke(DBusDaemon.this.dbus_server, args);
                    if ( null == rv ) {
                        send(cstruct, new MethodReturn("org.freedesktop.DBus", (MethodCall) msg, null), true);
                    }
                    else {
                        String sig = Marshalling.getDBusType(meth.getGenericReturnType())[ 0 ];
                        send(cstruct, new MethodReturn("org.freedesktop.DBus", (MethodCall) msg, sig, rv), true);
                    }
                }
                catch ( InvocationTargetException ITe ) {
                    log.warn(ITe);
                    send(cstruct, new org.freedesktop.dbus.Error("org.freedesktop.DBus", msg, ITe.getCause()));
                }
                catch ( DBusExecutionException DBEe ) {
                    log.warn(DBEe);
                    send(cstruct, new org.freedesktop.dbus.Error("org.freedesktop.DBus", msg, DBEe));
                }
                catch ( Exception e ) {
                    log.warn(e);
                    send(cstruct, new org.freedesktop.dbus.Error(
                        "org.freedesktop.DBus",
                        cstruct.unique,
                        "org.freedesktop.DBus.Error.GeneralError",
                        msg.getSerial(),
                        "s",
                        "An error occurred while calling " + msg.getName()));
                }
            }
            catch ( NoSuchMethodException NSMe ) {
                send(
                    cstruct,
                    new org.freedesktop.dbus.Error("org.freedesktop.DBus", cstruct.unique, "org.freedesktop.DBus.Error.UnknownMethod", msg
                            .getSerial(), "s", "This service does not support " + msg.getName()));
            }

        }


        @Override
        public String Introspect () {
            return "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\"\n"
                    + "\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n" + "<node>\n"
                    + "  <interface name=\"org.freedesktop.DBus.Introspectable\">\n" + "    <method name=\"Introspect\">\n"
                    + "      <arg name=\"data\" direction=\"out\" type=\"s\"/>\n" + "    </method>\n" + "  </interface>\n"
                    + "  <interface name=\"org.freedesktop.DBus\">\n" + "    <method name=\"RequestName\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"in\" type=\"u\"/>\n"
                    + "      <arg direction=\"out\" type=\"u\"/>\n" + "    </method>\n" + "    <method name=\"ReleaseName\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"u\"/>\n" + "    </method>\n"
                    + "    <method name=\"StartServiceByName\">\n" + "      <arg direction=\"in\" type=\"s\"/>\n"
                    + "      <arg direction=\"in\" type=\"u\"/>\n" + "      <arg direction=\"out\" type=\"u\"/>\n" + "    </method>\n"
                    + "    <method name=\"Hello\">\n" + "      <arg direction=\"out\" type=\"s\"/>\n" + "    </method>\n"
                    + "    <method name=\"NameHasOwner\">\n" + "      <arg direction=\"in\" type=\"s\"/>\n"
                    + "      <arg direction=\"out\" type=\"b\"/>\n" + "    </method>\n" + "    <method name=\"ListNames\">\n"
                    + "      <arg direction=\"out\" type=\"as\"/>\n" + "    </method>\n" + "    <method name=\"ListActivatableNames\">\n"
                    + "      <arg direction=\"out\" type=\"as\"/>\n" + "    </method>\n" + "    <method name=\"AddMatch\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "    </method>\n" + "    <method name=\"RemoveMatch\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "    </method>\n" + "    <method name=\"GetNameOwner\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"s\"/>\n" + "    </method>\n"
                    + "    <method name=\"ListQueuedOwners\">\n" + "      <arg direction=\"in\" type=\"s\"/>\n"
                    + "      <arg direction=\"out\" type=\"as\"/>\n" + "    </method>\n" + "    <method name=\"GetConnectionUnixUser\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"u\"/>\n" + "    </method>\n"
                    + "    <method name=\"GetConnectionUnixProcessID\">\n" + "      <arg direction=\"in\" type=\"s\"/>\n"
                    + "      <arg direction=\"out\" type=\"u\"/>\n" + "    </method>\n"
                    + "    <method name=\"GetConnectionSELinuxSecurityContext\">\n" + "      <arg direction=\"in\" type=\"s\"/>\n"
                    + "      <arg direction=\"out\" type=\"ay\"/>\n" + "    </method>\n" + "    <method name=\"ReloadConfig\">\n" + "    </method>\n"
                    + "    <signal name=\"NameOwnerChanged\">\n" + "      <arg type=\"s\"/>\n" + "      <arg type=\"s\"/>\n"
                    + "      <arg type=\"s\"/>\n" + "    </signal>\n" + "    <signal name=\"NameLost\">\n" + "      <arg type=\"s\"/>\n"
                    + "    </signal>\n" + "    <signal name=\"NameAcquired\">\n" + "      <arg type=\"s\"/>\n" + "    </signal>\n"
                    + "  </interface>\n" + "</node>";
        }


        @Override
        public void Ping () {}


        @Override
        public void run () {
            while ( DBusDaemon.this._run ) {
                Message msg;
                List<WeakReference<Connstruct>> wcs;
                // block on outqueue
                synchronized ( DBusDaemon.this.localqueue ) {
                    while ( DBusDaemon.this.localqueue.size() == 0 )
                        try {
                            DBusDaemon.this.localqueue.wait();
                        }
                        catch ( InterruptedException Ie ) {}
                    msg = DBusDaemon.this.localqueue.head();
                    wcs = DBusDaemon.this.localqueue.remove(msg);
                }
                if ( null != wcs ) {
                    try {
                        for ( WeakReference<Connstruct> wc : wcs ) {
                            Connstruct connstruct = wc.get();
                            if ( null != connstruct ) {
                                if ( log.isTraceEnabled() ) {
                                    log.trace("<localqueue> Got message " + msg + " from " + connstruct);
                                }
                                handleMessage(connstruct, msg);
                            }
                        }
                    }
                    catch ( DBusException DBe ) {
                        log.warn(DBe);
                    }
                }
                else {
                    log.info("Discarding " + msg + " connection reaped");
                }
            }
        }
    }

    public class Sender extends Thread {

        public Sender () {
            setName("Sender");
        }


        @Override
        public void run () {
            while ( DBusDaemon.this._run ) {

                if ( log.isTraceEnabled() ) {
                    log.trace("Acquiring lock on outqueue and blocking for data");
                }
                Message m = null;
                List<WeakReference<Connstruct>> wcs = null;
                // block on outqueue
                synchronized ( DBusDaemon.this.outqueue ) {
                    while ( DBusDaemon.this.outqueue.size() == 0 )
                        try {
                            DBusDaemon.this.outqueue.wait();
                        }
                        catch ( InterruptedException Ie ) {}

                    m = DBusDaemon.this.outqueue.head();
                    wcs = DBusDaemon.this.outqueue.remove(m);
                }
                if ( null != wcs ) {
                    for ( WeakReference<Connstruct> wc : wcs ) {
                        Connstruct c = wc.get();
                        if ( null != c ) {
                            if ( log.isTraceEnabled() ) {
                                log.trace("<outqueue> Got message " + m + " for " + c.unique);
                            }
                            log.info("Sending message " + m + " to " + c.unique);
                            try {
                                c.mout.writeMessage(m);
                            }
                            catch ( IOException IOe ) {
                                log.warn(IOe);
                                removeConnection(c);
                            }
                        }
                    }
                }
                else {
                    log.info("Discarding " + m + " connection reaped");
                }
            }
        }
    }

    public class Reader extends Thread {

        private Connstruct conn;
        private WeakReference<Connstruct> weakconn;
        private boolean _lrun = true;


        public Reader ( Connstruct conn ) {
            this.conn = conn;
            this.weakconn = new WeakReference<>(conn);
            setName("Reader");
        }


        public void stopRunning () {
            this._lrun = false;
        }


        @Override
        public void run () {
            while ( DBusDaemon.this._run && this._lrun ) {

                Message m = null;
                try {
                    m = this.conn.min.readMessage();
                }
                catch ( IOException IOe ) {
                    log.warn(IOe);
                    removeConnection(this.conn);
                }
                catch ( DBusException DBe ) {
                    log.warn(DBe);
                    if ( DBe instanceof FatalException )
                        removeConnection(this.conn);
                }

                if ( null != m ) {
                    log.info("Read " + m + " from " + this.conn.unique);
                    synchronized ( DBusDaemon.this.inqueue ) {
                        DBusDaemon.this.inqueue.putLast(m, this.weakconn);
                        DBusDaemon.this.inqueue.notifyAll();
                    }
                }
            }
            this.conn = null;
        }
    }

    private Map<Connstruct, Reader> conns = new HashMap<>();
    HashMap<String, Connstruct> names = new HashMap<>();
    MagicMap<Message, WeakReference<Connstruct>> outqueue = new MagicMap<>("out");
    MagicMap<Message, WeakReference<Connstruct>> inqueue = new MagicMap<>("in");
    MagicMap<Message, WeakReference<Connstruct>> localqueue = new MagicMap<>("local");
    List<Connstruct> sigrecips = new Vector<>();
    boolean _run = true;
    int next_unique = 0;
    Object unique_lock = new Object();
    DBusServer dbus_server = new DBusServer();
    Sender sender = new Sender();


    public DBusDaemon () {
        setName("Daemon");
        synchronized ( this.names ) {
            this.names.put("org.freedesktop.DBus", null);
        }
    }


    void send ( Connstruct c, Message m ) {
        send(c, m, false);
    }


    void send ( Connstruct c, Message m, boolean head ) {
        if ( log.isTraceEnabled() ) {

            if ( null == c ) {
                log.trace("Queing message " + m + " for all connections");
            }
            else {
                log.trace("Queing message " + m + " for " + c.unique);
            }
        }
        // send to all connections
        if ( null == c ) {
            synchronized ( this.conns ) {
                synchronized ( this.outqueue ) {
                    for ( Connstruct d : this.conns.keySet() )
                        if ( head )
                            this.outqueue.putFirst(m, new WeakReference<>(d));
                        else
                            this.outqueue.putLast(m, new WeakReference<>(d));
                    this.outqueue.notifyAll();
                }
            }
        }
        else {
            synchronized ( this.outqueue ) {
                if ( head )
                    this.outqueue.putFirst(m, new WeakReference<>(c));
                else
                    this.outqueue.putLast(m, new WeakReference<>(c));
                this.outqueue.notifyAll();
            }
        }
    }


    private List<Connstruct> findSignalMatches ( DBusSignal sig ) {
        List<Connstruct> l;
        synchronized ( this.sigrecips ) {
            l = new Vector<>(this.sigrecips);
        }
        return l;
    }


    @Override
    public void run () {
        while ( this._run ) {
            try {
                Message m;
                List<WeakReference<Connstruct>> wcs;
                synchronized ( this.inqueue ) {
                    while ( 0 == this.inqueue.size() )
                        try {
                            this.inqueue.wait();
                        }
                        catch ( InterruptedException Ie ) {}

                    m = this.inqueue.head();
                    wcs = this.inqueue.remove(m);
                }
                if ( null != wcs ) {
                    for ( WeakReference<Connstruct> wc : wcs ) {
                        Connstruct c = wc.get();
                        if ( null != c ) {
                            log.info("<inqueue> Got message " + m + " from " + c.unique);
                            // check if they have hello'd
                            if ( null == c.unique
                                    && ( ! ( m instanceof MethodCall ) || !"org.freedesktop.DBus".equals(m.getDestination()) || !"Hello".equals(m
                                            .getName()) ) ) {
                                send(c, new Error(
                                    "org.freedesktop.DBus",
                                    null,
                                    "org.freedesktop.DBus.Error.AccessDenied",
                                    m.getSerial(),
                                    "s",
                                    "You must send a Hello message"));
                            }
                            else {
                                try {
                                    if ( null != c.unique )
                                        m.setSource(c.unique);
                                }
                                catch ( DBusException DBe ) {
                                    log.warn(DBe);
                                    send(c, new Error(
                                        "org.freedesktop.DBus",
                                        null,
                                        "org.freedesktop.DBus.Error.GeneralError",
                                        m.getSerial(),
                                        "s",
                                        "Sending message failed"));
                                }

                                if ( "org.freedesktop.DBus".equals(m.getDestination()) ) {
                                    synchronized ( this.localqueue ) {
                                        this.localqueue.putLast(m, wc);
                                        this.localqueue.notifyAll();
                                    }
                                }
                                else {
                                    if ( m instanceof DBusSignal ) {
                                        List<Connstruct> list = findSignalMatches((DBusSignal) m);
                                        for ( Connstruct d : list )
                                            send(d, m);
                                    }
                                    else {
                                        Connstruct dest = this.names.get(m.getDestination());

                                        if ( null == dest ) {
                                            send(
                                                c,
                                                new Error(
                                                    "org.freedesktop.DBus",
                                                    null,
                                                    "org.freedesktop.DBus.Error.ServiceUnknown",
                                                    m.getSerial(),
                                                    "s",
                                                    String.format("The name `%s' does not exist", m.getDestination())));
                                        }
                                        else
                                            send(dest, m);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch ( DBusException DBe ) {
                log.warn(DBe);
            }
        }
    }


    void removeConnection ( Connstruct c ) {
        boolean exists;
        synchronized ( this.conns ) {
            exists = this.conns.containsKey(c);
            if ( exists ) {
                Reader r = this.conns.get(c);
                r.stopRunning();
                this.conns.remove(c);
            }
        }
        if ( exists ) {
            try {
                if ( null != c.sock )
                    c.sock.close();
            }
            catch ( IOException IOe ) {}
            synchronized ( this.names ) {
                List<String> toRemove = new Vector<>();
                for ( String name : this.names.keySet() )
                    if ( this.names.get(name) == c ) {
                        toRemove.add(name);
                        try {
                            send(null, new DBusSignal(
                                "org.freedesktop.DBus",
                                "/org/freedesktop/DBus",
                                "org.freedesktop.DBus",
                                "NameOwnerChanged",
                                "sss",
                                name,
                                c.unique,
                                ""));
                        }
                        catch ( DBusException DBe ) {
                            log.warn(DBe);
                        }
                    }
                for ( String name : toRemove )
                    this.names.remove(name);
            }
        }
    }


    public void addSock ( Socket s ) throws IOException {
        log.info("New Client");
        Connstruct c = new Connstruct(s);
        Reader r = new Reader(c);
        synchronized ( this.conns ) {
            this.conns.put(c, r);
        }
        r.start();
    }


    public static void syntax () {
        System.out
                .println("Syntax: DBusDaemon [--version] [-v] [--help] [-h] [--listen address] [-l address] [--print-address] [-r] [--pidfile file] [-p file] [--addressfile file] [-a file] [--unix] [-u] [--tcp] [-t] ");
        System.exit(1);
    }


    public static void version () {
        System.out.println("D-Bus Java Version: " + System.getProperty("Version"));
        System.exit(1);
    }


    public static void saveFile ( String data, String file ) throws IOException {
        try ( PrintWriter w = new PrintWriter(new FileOutputStream(file)) ) {
            w.println(data);
        }
    }


    public static void main ( String args[] ) throws Exception {
        String addr = null;
        String pidfile = null;
        String addrfile = null;
        boolean printaddress = false;
        boolean unix = true;
        boolean tcp = false;

        // parse options
        try {
            for ( int i = 0; i < args.length; i++ )
                if ( "--help".equals(args[ i ]) || "-h".equals(args[ i ]) )
                    syntax();
                else if ( "--version".equals(args[ i ]) || "-v".equals(args[ i ]) )
                    version();
                else if ( "--listen".equals(args[ i ]) || "-l".equals(args[ i ]) )
                    addr = args[ ++i ];
                else if ( "--pidfile".equals(args[ i ]) || "-p".equals(args[ i ]) )
                    pidfile = args[ ++i ];
                else if ( "--addressfile".equals(args[ i ]) || "-a".equals(args[ i ]) )
                    addrfile = args[ ++i ];
                else if ( "--print-address".equals(args[ i ]) || "-r".equals(args[ i ]) )
                    printaddress = true;
                else if ( "--unix".equals(args[ i ]) || "-u".equals(args[ i ]) ) {
                    unix = true;
                    tcp = false;
                }
                else if ( "--tcp".equals(args[ i ]) || "-t".equals(args[ i ]) ) {
                    tcp = true;
                    unix = false;
                }
                else
                    syntax();
        }
        catch ( ArrayIndexOutOfBoundsException AIOOBe ) {
            syntax();
        }

        // generate a random address if none specified
        if ( null == addr && unix )
            addr = DirectConnection.createDynamicSession();
        else if ( null == addr && tcp )
            addr = DirectConnection.createDynamicTCPSession();

        BusAddress address = new BusAddress(addr);
        if ( null == address.getParameter("guid") ) {
            addr += ",guid=" + Transport.genGUID();
            address = new BusAddress(addr);
        }

        // print address to stdout
        if ( printaddress )
            System.out.println(addr);

        // print address to file
        if ( null != addrfile )
            saveFile(addr, addrfile);

        // print PID to file
        if ( null != pidfile )
            saveFile(System.getProperty("Pid"), pidfile);

        // start the daemon
        log.info("Binding to " + addr);
        if ( "unix".equals(address.getType()) )
            doUnix(address);
        else if ( "tcp".equals(address.getType()) )
            doTCP(address);
        else
            throw new Exception("Unknown address type: " + address.getType());
    }


    private static void doUnix ( BusAddress address ) throws IOException {
        try ( AFUNIXServerSocket uss = AFUNIXServerSocket.newInstance() ) {
            File sockFile = null;
            if ( null != address.getParameter("abstract") )
                sockFile = new File(address.getParameter("abstract"));
            else
                sockFile = new File(address.getParameter("path"));
            uss.bind(new AFUNIXSocketAddress(sockFile));

            DBusDaemon d = new DBusDaemon();
            d.start();
            d.sender.start();
            d.dbus_server.start();

            // accept new connections
            while ( d._run ) {
                @SuppressWarnings ( "resource" )
                AFUNIXSocket s = uss.accept();
                if ( ( new Transport.SASL() ).auth(
                    Transport.SASL.MODE_SERVER,
                    Transport.SASL.AUTH_EXTERNAL,
                    address.getParameter("guid"),
                    s.getOutputStream(),
                    s.getInputStream(),
                    s) ) {
                    // s.setBlocking(false);
                    d.addSock(s);
                }
                else {
                    s.close();
                }
            }
        }
    }


    private static void doTCP ( BusAddress address ) throws IOException {
        try ( ServerSocket ss = new ServerSocket(Integer.parseInt(address.getParameter("port")), 10, InetAddress.getByName(address
                .getParameter("host"))) ) {
            DBusDaemon d = new DBusDaemon();
            d.start();
            d.sender.start();
            d.dbus_server.start();

            // accept new connections
            while ( d._run ) {
                @SuppressWarnings ( "resource" )
                Socket s = ss.accept();
                boolean authOK = false;
                try {
                    authOK = ( new Transport.SASL() ).auth(
                        Transport.SASL.MODE_SERVER,
                        Transport.SASL.AUTH_EXTERNAL,
                        address.getParameter("guid"),
                        s.getOutputStream(),
                        s.getInputStream(),
                        null);
                }
                catch ( Exception e ) {
                    log.warn(e);
                }
                if ( authOK ) {
                    d.addSock(s);
                }
                else
                    s.close();
            }
        }
    }
}
