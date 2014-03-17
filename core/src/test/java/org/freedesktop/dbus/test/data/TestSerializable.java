/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.test.data;


import java.util.List;
import java.util.Vector;

import org.freedesktop.dbus.DBusSerializable;
import org.freedesktop.dbus.exceptions.DBusException;


public class TestSerializable <A> implements DBusSerializable {

    private int a;
    private String b;
    private Vector<Integer> c;


    public TestSerializable ( int a, A b, Vector<Integer> c ) {
        this.a = a;
        this.b = b.toString();
        this.c = c;
    }


    public TestSerializable () {}


    public void deserialize ( int da, String db, List<Integer> dc ) {
        this.a = da;
        this.b = db;
        this.c = new Vector<>(dc);
    }


    @Override
    public Object[] serialize () throws DBusException {
        return new Object[] {
            this.a, this.b, this.c
        };
    }


    public int getInt () {
        return this.a;
    }


    public String getString () {
        return this.b;
    }


    public Vector<Integer> getVector () {
        return this.c;
    }


    @Override
    public String toString () {
        return "TestSerializable{" + this.a + "," + this.b + "," + this.c + "}";
    }
}
