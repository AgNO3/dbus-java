/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


/**
 * Provides a long =&gt; MethodCall map which doesn't allocate objects
 * on insertion/removal. Keys must be inserted in ascending order.
 */
class EfficientMap {

    private long[] kv;
    private MethodCall[] vv;
    private int start;
    private int end;
    private int init_size;


    public EfficientMap ( int initial_size ) {
        this.init_size = initial_size;
        shrink();
    }


    private void grow () {
        // create new vectors twice as long
        long[] oldkv = this.kv;
        this.kv = new long[oldkv.length * 2];
        MethodCall[] oldvv = this.vv;
        this.vv = new MethodCall[oldvv.length * 2];

        // copy start->length to the start of the new vector
        System.arraycopy(oldkv, this.start, this.kv, 0, oldkv.length - this.start);
        System.arraycopy(oldvv, this.start, this.vv, 0, oldvv.length - this.start);
        // copy 0->end to the next part of the new vector
        if ( this.end != ( oldkv.length - 1 ) ) {
            System.arraycopy(oldkv, 0, this.kv, oldkv.length - this.start, this.end + 1);
            System.arraycopy(oldvv, 0, this.vv, oldvv.length - this.start, this.end + 1);
        }
        // reposition pointers
        this.start = 0;
        this.end = oldkv.length;
    }


    // create a new vector with just the valid keys in and return it
    public long[] getKeys () {
        int size;
        if ( this.start < this.end )
            size = this.end - this.start;
        else
            size = this.kv.length - ( this.start - this.end );
        long[] lv = new long[size];
        int copya;
        if ( size > this.kv.length - this.start )
            copya = this.kv.length - this.start;
        else
            copya = size;
        System.arraycopy(this.kv, this.start, lv, 0, copya);
        if ( copya < size ) {
            System.arraycopy(this.kv, 0, lv, copya, size - copya);
        }
        return lv;
    }


    private void shrink () {
        if ( null != this.kv && this.kv.length == this.init_size )
            return;
        // reset to original size
        this.kv = new long[this.init_size];
        this.vv = new MethodCall[this.init_size];
        this.start = 0;
        this.end = 0;
    }


    public void put ( long l, MethodCall m ) {
        // put this at the end
        this.kv[ this.end ] = l;
        this.vv[ this.end ] = m;
        // move the end
        if ( this.end == ( this.kv.length - 1 ) )
            this.end = 0;
        else
            this.end++;
        // if we are out of space, grow.
        if ( this.end == this.start )
            grow();
    }


    public MethodCall remove ( long l ) {
        // find the item
        int pos = find(l);
        // if we don't have it return null
        if ( -1 == pos )
            return null;
        // get the value
        MethodCall m = this.vv[ pos ];
        // set it as unused
        this.vv[ pos ] = null;
        this.kv[ pos ] = -1;
        // move the pointer to the first full element
        while ( -1 == this.kv[ this.start ] ) {
            if ( this.start == ( this.kv.length - 1 ) )
                this.start = 0;
            else
                this.start++;
            // if we have emptied the list, shrink it
            if ( this.start == this.end ) {
                shrink();
                break;
            }
        }
        return m;
    }


    public boolean contains ( long l ) {
        // check if find succeeds
        return -1 != find(l);
    }


    /* could binary search, but it's probably the first one */
    private int find ( long l ) {
        int i = this.start;
        while ( i != this.end && this.kv[ i ] != l )
            if ( i == ( this.kv.length - 1 ) )
                i = 0;
            else
                i++;
        if ( i == this.end )
            return -1;
        return i;
    }
}
