/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;


public class MessageWriter {

    private static final Logger log = Logger.getLogger(MessageWriter.class);

    private OutputStream out;
    private boolean isunix;


    public MessageWriter ( OutputStream out ) {
        this.out = out;
        this.isunix = false;

        if ( !this.isunix )
            this.out = new BufferedOutputStream(this.out);
    }


    public void writeMessage ( Message m ) throws IOException {
        log.info("<= " + m);

        if ( null == m )
            return;
        if ( null == m.getWireData() ) {
            log.warn("Message " + m + " wire-data was null!");
            return;
        }

        for ( byte[] buf : m.getWireData() ) {
            if ( log.isTraceEnabled() ) {
                Hex h = new Hex();
                log.trace("(" + buf + "):" + ( null == buf ? "" : h.encode(buf) ));
            }
            if ( null == buf )
                break;
            this.out.write(buf);
        }
        this.out.flush();
    }


    public void close () throws IOException {
        log.debug("Closing Message Writer");
        this.out.close();
    }
}
