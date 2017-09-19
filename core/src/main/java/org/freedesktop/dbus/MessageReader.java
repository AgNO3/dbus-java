/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageProtocolVersionException;
import org.freedesktop.dbus.exceptions.MessageTypeException;


public class MessageReader {

    private static final Logger log = Logger.getLogger(MessageReader.class);

    private InputStream in;
    private byte[] buf = null;
    private byte[] tbuf = null;
    private byte[] header = null;
    private byte[] body = null;
    private int[] len = new int[4];


    public MessageReader ( InputStream in ) {
        this.in = new BufferedInputStream(in);
    }


    public Message readMessage () throws IOException, DBusException {
        int rv;
        /* Read the 12 byte fixed header, retrying as neccessary */
        if ( null == this.buf ) {
            this.buf = new byte[12];
            this.len[ 0 ] = 0;
        }
        if ( this.len[ 0 ] < 12 ) {
            try {
                rv = this.in.read(this.buf, this.len[ 0 ], 12 - this.len[ 0 ]);
            }
            catch ( SocketTimeoutException STe ) {
                return null;
            }
            if ( -1 == rv )
                throw new EOFException("Underlying transport returned EOF");
            this.len[ 0 ] += rv;
        }
        if ( this.len[ 0 ] == 0 )
            return null;
        if ( this.len[ 0 ] < 12 ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Only got " + this.len[ 0 ] + " of 12 bytes of header");
            }
            return null;
        }

        /* Parse the details from the header */
        byte endian = this.buf[ 0 ];
        byte type = this.buf[ 1 ];
        byte protover = this.buf[ 3 ];
        if ( protover > Message.PROTOCOL ) {
            this.buf = null;
            throw new MessageProtocolVersionException(String.format("Protocol version %s is unsupported", protover));
        }

        /* Read the length of the variable header */
        if ( null == this.tbuf ) {
            this.tbuf = new byte[4];
            this.len[ 1 ] = 0;
        }
        if ( this.len[ 1 ] < 4 ) {
            try {
                rv = this.in.read(this.tbuf, this.len[ 1 ], 4 - this.len[ 1 ]);
            }
            catch ( SocketTimeoutException STe ) {
                log.debug("Socket timeout", STe);
                return null;
            }
            if ( -1 == rv )
                throw new EOFException("Underlying transport returned EOF");
            this.len[ 1 ] += rv;
        }
        if ( this.len[ 1 ] < 4 ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Only got " + this.len[ 1 ] + " of 4 bytes of header");
            }
            return null;
        }

        /* Parse the variable header length */
        int headerlen = 0;
        if ( null == this.header ) {
            headerlen = (int) Message.demarshallint(this.tbuf, 0, endian, 4);
            if ( 0 != headerlen % 8 )
                headerlen += 8 - ( headerlen % 8 );
        }
        else
            headerlen = this.header.length - 8;

        /* Read the variable header */
        if ( null == this.header ) {
            this.header = new byte[headerlen + 8];
            System.arraycopy(this.tbuf, 0, this.header, 0, 4);
            this.len[ 2 ] = 0;
        }
        if ( this.len[ 2 ] < headerlen ) {
            try {
                rv = this.in.read(this.header, 8 + this.len[ 2 ], headerlen - this.len[ 2 ]);
            }
            catch ( SocketTimeoutException STe ) {
                log.debug("Socket timeout", STe);
                return null;
            }
            if ( -1 == rv )
                throw new EOFException("Underlying transport returned EOF");
            this.len[ 2 ] += rv;
        }
        if ( this.len[ 2 ] < headerlen ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Only got " + this.len[ 2 ] + " of " + headerlen + " bytes of header");
            }
            return null;
        }

        /* Read the body */
        int bodylen = 0;
        if ( null == this.body )
            bodylen = (int) Message.demarshallint(this.buf, 4, endian, 4);
        if ( null == this.body ) {
            this.body = new byte[bodylen];
            this.len[ 3 ] = 0;
        }
        if ( this.len[ 3 ] < this.body.length ) {
            try {
                rv = this.in.read(this.body, this.len[ 3 ], this.body.length - this.len[ 3 ]);
            }
            catch ( SocketTimeoutException STe ) {
                log.debug("Socket timeout", STe);
                return null;
            }
            if ( -1 == rv )
                throw new EOFException("Underlying transport returned EOF");
            this.len[ 3 ] += rv;
        }
        if ( this.len[ 3 ] < this.body.length ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Only got " + this.len[ 3 ] + " of " + this.body.length + " bytes of body");
            }
            return null;
        }

        Message m;
        switch ( type ) {
        case Message.MessageType.METHOD_CALL:
            m = new MethodCall();
            break;
        case Message.MessageType.METHOD_RETURN:
            m = new MethodReturn();
            break;
        case Message.MessageType.SIGNAL:
            m = new DBusSignal();
            break;
        case Message.MessageType.ERROR:
            m = new Error();
            break;
        default:
            throw new MessageTypeException(String.format("Message type %s unsupported", type));
        }
        if ( log.isTraceEnabled() ) {
            Hex h = new Hex();
            log.trace(h.encode(this.buf));
            log.trace(h.encode(this.tbuf));
            log.trace(h.encode(this.header));
            log.trace(h.encode(this.body));
        }
        try {
            m.populate(this.buf, this.header, this.body);
        }
        catch ( DBusException DBe ) {
            this.buf = null;
            this.tbuf = null;
            this.body = null;
            this.header = null;
            throw DBe;
        }
        catch ( RuntimeException Re ) {
            this.buf = null;
            this.tbuf = null;
            this.body = null;
            this.header = null;
            throw Re;
        }

        log.info("=> " + m);

        this.buf = null;
        this.tbuf = null;
        this.body = null;
        this.header = null;
        return m;
    }


    public void close () throws IOException {
        log.info("Closing Message Reader");
        this.in.close();
    }
}
