/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.util.HashMap;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;


public class DBusMatchRule {

    /* signal, error, method_call, method_reply */
    private String type;
    private String iface;
    private String member;
    private String object;
    private String source;
    private static HashMap<String, Class<? extends DBusSignal>> signalTypeMap = new HashMap<>();


    static Class<? extends DBusSignal> getCachedSignalType ( String type ) {
        return signalTypeMap.get(type);
    }


    public DBusMatchRule ( String type, String iface, String member ) {
        this.type = type;
        this.iface = iface;
        this.member = member;
    }


    public DBusMatchRule ( DBusExecutionException e ) throws DBusException {
        this(e.getClass());
        this.member = null;
        this.type = "error";
    }


    public DBusMatchRule ( Message m ) {
        this.iface = m.getInterface();
        this.member = m.getName();
        if ( m instanceof DBusSignal )
            this.type = "signal";
        else if ( m instanceof Error ) {
            this.type = "error";
            this.member = null;
        }
        else if ( m instanceof MethodCall )
            this.type = "method_call";
        else if ( m instanceof MethodReturn )
            this.type = "method_reply";
    }


    public DBusMatchRule ( Class<? extends DBusInterface> c, String method ) throws DBusException {
        this(c);
        this.member = method;
        this.type = "method_call";
    }


    public DBusMatchRule ( Class<? extends Object> c, String source, String object ) throws DBusException {
        this(c);
        this.source = source;
        this.object = object;
    }


    @SuppressWarnings ( "unchecked" )
    public DBusMatchRule ( Class<? extends Object> c ) throws DBusException {
        if ( DBusInterface.class.isAssignableFrom(c) ) {
            if ( null != c.getAnnotation(DBusInterfaceName.class) )
                this.iface = c.getAnnotation(DBusInterfaceName.class).value();
            else
                this.iface = AbstractConnection.dollar_pattern.matcher(c.getName()).replaceAll(".");
            if ( !this.iface.matches(".*\\..*") )
                throw new DBusException("DBusInterfaces must be defined in a package.");
            this.member = null;
            this.type = null;
        }
        else if ( DBusSignal.class.isAssignableFrom(c) ) {
            if ( null == c.getEnclosingClass() )
                throw new DBusException(
                    "Signals must be declared as a member of a class implementing DBusInterface which is the member of a package.");
            else if ( null != c.getEnclosingClass().getAnnotation(DBusInterfaceName.class) )
                this.iface = c.getEnclosingClass().getAnnotation(DBusInterfaceName.class).value();
            else
                this.iface = AbstractConnection.dollar_pattern.matcher(c.getEnclosingClass().getName()).replaceAll(".");
            // Don't export things which are invalid D-Bus interfaces
            if ( !this.iface.matches(".*\\..*") )
                throw new DBusException("DBusInterfaces must be defined in a package.");
            if ( c.isAnnotationPresent(DBusMemberName.class) )
                this.member = c.getAnnotation(DBusMemberName.class).value();
            else
                this.member = c.getSimpleName();
            signalTypeMap.put(this.iface + '$' + this.member, (Class<? extends DBusSignal>) c);
            this.type = "signal";
        }
        else if ( Error.class.isAssignableFrom(c) ) {
            if ( null != c.getAnnotation(DBusInterfaceName.class) )
                this.iface = c.getAnnotation(DBusInterfaceName.class).value();
            else
                this.iface = AbstractConnection.dollar_pattern.matcher(c.getName()).replaceAll(".");
            if ( !this.iface.matches(".*\\..*") )
                throw new DBusException("DBusInterfaces must be defined in a package.");
            this.member = null;
            this.type = "error";
        }
        else if ( DBusExecutionException.class.isAssignableFrom(c) ) {
            if ( null != c.getClass().getAnnotation(DBusInterfaceName.class) )
                this.iface = c.getClass().getAnnotation(DBusInterfaceName.class).value();
            else
                this.iface = AbstractConnection.dollar_pattern.matcher(c.getClass().getName()).replaceAll(".");
            if ( !this.iface.matches(".*\\..*") )
                throw new DBusException("DBusInterfaces must be defined in a package.");
            this.member = null;
            this.type = "error";
        }
        else
            throw new DBusException("Invalid type for match rule: " + c);
    }


    @Override
    public String toString () {
        String s = null;
        if ( null != this.type )
            s = "type='" + this.type + "'";
        if ( null != this.member )
            s = null == s ? "member='" + this.member + "'" : s + ",member='" + this.member + "'";
        if ( null != this.iface )
            s = null == s ? "interface='" + this.iface + "'" : s + ",interface='" + this.iface + "'";
        if ( null != this.source )
            s = null == s ? "sender='" + this.source + "'" : s + ",sender='" + this.source + "'";
        if ( null != this.object )
            s = null == s ? "path='" + this.object + "'" : s + ",path='" + this.object + "'";
        return s;
    }


    public String getType () {
        return this.type;
    }


    public String getInterface () {
        return this.iface;
    }


    public String getMember () {
        return this.member;
    }


    public String getSource () {
        return this.source;
    }


    public String getObject () {
        return this.object;
    }

}
