/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageFormatException;


public class MethodCall extends Message {

    private static final Logger log = Logger.getLogger(MethodCall.class);


    MethodCall () {}


    public MethodCall ( String dest, String path, String iface, String member, byte flags, String sig, Object... args ) throws DBusException {
        this(null, dest, path, iface, member, flags, sig, args);
    }


    public MethodCall ( String source, String dest, String path, String iface, String member, byte flags, String sig, Object... args )
            throws DBusException {
        super(Message.Endian.BIG, Message.MessageType.METHOD_CALL, flags);

        if ( null == member || null == path )
            throw new MessageFormatException("Must specify destination, path and function name to MethodCalls.");
        this.headers.put(Message.HeaderField.PATH, path);
        this.headers.put(Message.HeaderField.MEMBER, member);

        Vector<Object> hargs = new Vector<>();

        hargs.add(new Object[] {
            Message.HeaderField.PATH, new Object[] {
                ArgumentType.OBJECT_PATH_STRING, path
            }
        });

        if ( null != source ) {
            this.headers.put(Message.HeaderField.SENDER, source);
            hargs.add(new Object[] {
                Message.HeaderField.SENDER, new Object[] {
                    ArgumentType.STRING_STRING, source
                }
            });
        }

        if ( null != dest ) {
            this.headers.put(Message.HeaderField.DESTINATION, dest);
            hargs.add(new Object[] {
                Message.HeaderField.DESTINATION, new Object[] {
                    ArgumentType.STRING_STRING, dest
                }
            });
        }

        if ( null != iface ) {
            hargs.add(new Object[] {
                Message.HeaderField.INTERFACE, new Object[] {
                    ArgumentType.STRING_STRING, iface
                }
            });
            this.headers.put(Message.HeaderField.INTERFACE, iface);
        }

        hargs.add(new Object[] {
            Message.HeaderField.MEMBER, new Object[] {
                ArgumentType.STRING_STRING, member
            }
        });

        if ( null != sig ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Appending arguments with signature: " + sig);
            }
            hargs.add(new Object[] {
                Message.HeaderField.SIGNATURE, new Object[] {
                    ArgumentType.SIGNATURE_STRING, sig
                }
            });
            this.headers.put(Message.HeaderField.SIGNATURE, sig);
            setArgs(args);
        }

        byte[] blen = new byte[4];
        appendBytes(blen);
        append("ua(yv)", this.serial, hargs.toArray());
        pad((byte) 8);

        long c = this.bytecounter;
        if ( null != sig )
            append(sig, args);

        if ( log.isDebugEnabled() ) {
            log.debug("Appended body, type: " + sig + " start: " + c + " end: " + this.bytecounter + " size: " + ( this.bytecounter - c ));
        }
        marshallint(this.bytecounter - c, blen, 0, 4);
        if ( log.isDebugEnabled() ) {
            Hex h = new Hex();
            log.debug("marshalled size (" + blen + "): " + h.encode(blen));
        }
    }

    private static long REPLY_WAIT_TIMEOUT = 20000;


    /**
     * Set the default timeout for method calls.
     * Default is 20s.
     * 
     * @param timeout
     *            New timeout in ms.
     */
    public static void setDefaultTimeout ( long timeout ) {
        REPLY_WAIT_TIMEOUT = timeout;
    }

    Message reply = null;


    public synchronized boolean hasReply () {
        return null != this.reply;
    }


    /**
     * Block (if neccessary) for a reply.
     * 
     * @return The reply to this MethodCall, or null if a timeout happens.
     * @param timeout
     *            The length of time to block before timing out (ms).
     */
    public synchronized Message getReply ( long timeout ) {
        if ( log.isTraceEnabled() ) {
            log.trace("Blocking on " + this);
        }
        if ( null != this.reply )
            return this.reply;
        try {
            wait(timeout);
            return this.reply;
        }
        catch ( InterruptedException Ie ) {
            return this.reply;
        }
    }


    /**
     * Block (if neccessary) for a reply.
     * Default timeout is 20s, or can be configured with setDefaultTimeout()
     * 
     * @return The reply to this MethodCall, or null if a timeout happens.
     */
    public synchronized Message getReply () {
        if ( log.isTraceEnabled() ) {
            log.trace("Blocking on " + this);
        }
        if ( null != this.reply )
            return this.reply;
        try {
            wait(REPLY_WAIT_TIMEOUT);
            return this.reply;
        }
        catch ( InterruptedException Ie ) {
            return this.reply;
        }
    }


    protected synchronized void setReply ( Message reply ) {
        if ( log.isTraceEnabled() ) {
            log.trace("Setting reply to " + this + " to " + reply);
        }
        this.reply = reply;
        notifyAll();
    }

}
