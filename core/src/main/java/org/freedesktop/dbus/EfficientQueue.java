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


/**
 * Provides a Message queue which doesn't allocate objects
 * on insertion/removal.
 */
class EfficientQueue {

    private static final Logger log = Logger.getLogger(EfficientQueue.class);

    private Message[] mv;
    private int start;
    private int end;
    private int init_size;


    public EfficientQueue ( int initial_size ) {
        this.init_size = initial_size;
        shrink();
    }


    private void grow () {
        log.debug("Growing");
        // create new vectors twice as long
        Message[] oldmv = this.mv;
        this.mv = new Message[oldmv.length * 2];

        // copy start->length to the start of the new vector
        System.arraycopy(oldmv, this.start, this.mv, 0, oldmv.length - this.start);
        // copy 0->end to the next part of the new vector
        if ( this.end != ( oldmv.length - 1 ) ) {
            System.arraycopy(oldmv, 0, this.mv, oldmv.length - this.start, this.end + 1);
        }
        // reposition pointers
        this.start = 0;
        this.end = oldmv.length;
    }


    // create a new vector with just the valid keys in and return it
    public Message[] getKeys () {
        if ( this.start == this.end )
            return new Message[0];
        Message[] lv;
        if ( this.start < this.end ) {
            int size = this.end - this.start;
            lv = new Message[size];
            System.arraycopy(this.mv, this.start, lv, 0, size);
        }
        else {
            int size = this.mv.length - this.start + this.end;
            lv = new Message[size];
            System.arraycopy(this.mv, this.start, lv, 0, this.mv.length - this.start);
            System.arraycopy(this.mv, 0, lv, this.mv.length - this.start, this.end);
        }
        return lv;
    }


    private void shrink () {
        log.debug("Shrinking");
        if ( null != this.mv && this.mv.length == this.init_size )
            return;
        // reset to original size
        this.mv = new Message[this.init_size];
        this.start = 0;
        this.end = 0;
    }


    public void add ( Message m ) {
        if ( log.isDebugEnabled() ) {
            log.debug("Enqueueing Message " + m);
        }

        // put this at the end
        this.mv[ this.end ] = m;
        // move the end
        if ( this.end == ( this.mv.length - 1 ) )
            this.end = 0;
        else
            this.end++;
        // if we are out of space, grow.
        if ( this.end == this.start )
            grow();
    }


    public Message remove () {
        if ( this.start == this.end )
            return null;
        // find the item
        int pos = this.start;
        // get the value
        Message m = this.mv[ pos ];
        // set it as unused
        this.mv[ pos ] = null;
        if ( this.start == ( this.mv.length - 1 ) )
            this.start = 0;
        else
            this.start++;
        if ( log.isDebugEnabled() ) {
            log.debug("Dequeueing " + m);
        }
        return m;
    }


    public boolean isEmpty () {
        // check if find succeeds
        return this.start == this.end;
    }


    public int size () {
        if ( this.end >= this.start ) {
            return this.end - this.start;
        }
        return this.mv.length - this.start + this.end;
    }
}
