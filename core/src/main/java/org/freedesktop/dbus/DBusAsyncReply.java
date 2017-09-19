/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.DBus.Error.NoReply;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;


/**
 * A handle to an asynchronous method call.
 */
public class DBusAsyncReply <ReturnType> {

    private static final Logger log = Logger.getLogger(DBusAsyncReply.class);


    /**
     * Check if any of a set of asynchronous calls have had a reply.
     * 
     * @param replies
     *            A Collection of handles to replies to check.
     * @return A Collection only containing those calls which have had replies.
     */
    public static Collection<DBusAsyncReply<? extends Object>> hasReply ( Collection<DBusAsyncReply<? extends Object>> replies ) {
        Collection<DBusAsyncReply<? extends Object>> c = new ArrayList<>(replies);
        Iterator<DBusAsyncReply<? extends Object>> i = c.iterator();
        while ( i.hasNext() )
            if ( !i.next().hasReply() )
                i.remove();
        return c;
    }

    private ReturnType rval = null;
    private DBusExecutionException error = null;
    private MethodCall mc;
    private Method me;
    private AbstractConnection conn;


    DBusAsyncReply ( MethodCall mc, Method me, AbstractConnection conn ) {
        this.mc = mc;
        this.me = me;
        this.conn = conn;
    }


    @SuppressWarnings ( "unchecked" )
    private synchronized void checkReply () {
        if ( this.mc.hasReply() ) {
            Message m = this.mc.getReply();
            if ( m instanceof Error )
                this.error = ( (Error) m ).getException(this.conn);
            else if ( m instanceof MethodReturn ) {
                try {
                    this.rval = (ReturnType) RemoteInvocationHandler.convertRV(m.getSig(), m.getParameters(), this.me, this.conn);
                }
                catch ( DBusExecutionException DBEe ) {
                    this.error = DBEe;
                }
                catch ( DBusException DBe ) {
                    log.warn("Failed to get return value", DBe);
                    this.error = new DBusExecutionException(DBe.getMessage(), DBe);
                }
            }
        }
    }


    /**
     * Check if we've had a reply.
     * 
     * @return True if we have a reply
     */
    public boolean hasReply () {
        if ( null != this.rval || null != this.error )
            return true;
        checkReply();
        return null != this.rval || null != this.error;
    }


    /**
     * Get the reply.
     * 
     * @return The return value from the method.
     * @throws DBusExecutionException
     *             if the reply to the method was an error.
     * @throws NoReply
     *             if the method hasn't had a reply yet
     */
    public ReturnType getReply () throws DBusExecutionException {
        if ( null != this.rval )
            return this.rval;
        else if ( null != this.error )
            throw this.error;
        checkReply();
        if ( null != this.rval )
            return this.rval;
        else if ( null != this.error )
            throw this.error;
        else
            throw new NoReply("Async call has not had a reply");
    }


    @Override
    public String toString () {
        return "Waiting for: " + this.mc;
    }


    Method getMethod () {
        return this.me;
    }


    AbstractConnection getConnection () {
        return this.conn;
    }


    MethodCall getCall () {
        return this.mc;
    }
}
