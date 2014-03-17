/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;


public class BusAddress {

    private static final Logger log = Logger.getLogger(BusAddress.class);

    private String type;
    private Map<String, String> parameters;


    public BusAddress ( String address ) throws ParseException {
        if ( null == address || "".equals(address) )
            throw new ParseException("Bus address is blank", 0);
        if ( log.isTraceEnabled() ) {
            log.trace("Parsing bus address: " + address);
        }
        String[] ss = address.split(":", 2);
        if ( ss.length < 2 )
            throw new ParseException("Bus address is invalid: " + address, 0);
        this.type = ss[ 0 ];
        if ( log.isTraceEnabled() ) {
            log.trace("Transport type: " + this.type);
        }
        String[] ps = ss[ 1 ].split(",");
        this.parameters = new HashMap<>();
        for ( String p : ps ) {
            String[] kv = p.split("=", 2);
            this.parameters.put(kv[ 0 ], kv[ 1 ]);
        }
        if ( log.isTraceEnabled() ) {
            log.trace("Transport options: " + this.parameters);
        }
    }


    public String getType () {
        return this.type;
    }


    public String getParameter ( String key ) {
        return this.parameters.get(key);
    }


    @Override
    public String toString () {
        return this.type + ": " + this.parameters;
    }
}
