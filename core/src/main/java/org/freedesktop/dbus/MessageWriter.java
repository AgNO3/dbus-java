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
        // try {
        // if ( out instanceof USOutputStream )
        // this.isunix = true;
        // }
        // catch ( Throwable t ) {}
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
        // if ( this.isunix ) {
        // if ( log.isDebugEnabled() ) {
        // log.debug("Writing all " + m.getWireData().length + " buffers simultaneously to Unix Socket");
        //
        // if ( log.isTraceEnabled() ) {
        // Hex h = new Hex();
        // for ( byte[] buf : m.getWireData() )
        // log.trace("(" + buf + "):" + ( null == buf ? "" : h.encode(buf) ));
        // }
        // }
        // ( (USOutputStream) this.out ).write(m.getWireData());
        // }
        // else
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
        log.info("Closing Message Writer");
        this.out.close();
    }
}
