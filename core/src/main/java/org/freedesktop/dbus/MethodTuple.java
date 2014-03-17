/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import org.apache.log4j.Logger;


class MethodTuple {

    private static final Logger log = Logger.getLogger(MethodTuple.class);

    String name;
    String sig;


    public MethodTuple ( String name, String sig ) {
        this.name = name;
        if ( null != sig )
            this.sig = sig;
        else
            this.sig = "";
        if ( log.isTraceEnabled() ) {
            log.trace("new MethodTuple(" + this.name + ", " + this.sig + ")");
        }
    }


    @Override
    public boolean equals ( Object o ) {
        return o.getClass().equals(MethodTuple.class) && ( (MethodTuple) o ).name.equals(this.name) && ( (MethodTuple) o ).sig.equals(this.sig);
    }


    @Override
    public int hashCode () {
        return this.name.hashCode() + this.sig.hashCode();
    }
}
