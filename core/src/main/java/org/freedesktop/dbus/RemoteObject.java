/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


public class RemoteObject {

    String busname;
    String objectpath;
    Class<? extends DBusInterface> iface;
    boolean autostart;


    public RemoteObject ( String busname, String objectpath, Class<? extends DBusInterface> iface, boolean autostart ) {
        this.busname = busname;
        this.objectpath = objectpath;
        this.iface = iface;
        this.autostart = autostart;
    }


    @Override
    public boolean equals ( Object o ) {
        if ( ! ( o instanceof RemoteObject ) )
            return false;
        RemoteObject them = (RemoteObject) o;

        if ( !them.objectpath.equals(this.objectpath) )
            return false;

        if ( null == this.busname && null != them.busname )
            return false;
        if ( null != this.busname && null == them.busname )
            return false;
        if ( null != them.busname && !them.busname.equals(this.busname) )
            return false;

        if ( null == this.iface && null != them.iface )
            return false;
        if ( null != this.iface && null == them.iface )
            return false;
        if ( null != them.iface && !them.iface.equals(this.iface) )
            return false;

        return true;
    }


    @Override
    public int hashCode () {
        return ( null == this.busname ? 0 : this.busname.hashCode() ) + this.objectpath.hashCode()
                + ( null == this.iface ? 0 : this.iface.hashCode() );
    }


    public boolean autoStarting () {
        return this.autostart;
    }


    public String getBusName () {
        return this.busname;
    }


    public String getObjectPath () {
        return this.objectpath;
    }


    public Class<? extends DBusInterface> getInterface () {
        return this.iface;
    }


    @Override
    public String toString () {
        return this.busname + ":" + this.objectpath + ":" + this.iface;
    }
}
