/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.test;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.freedesktop.dbus.DBus;
import org.freedesktop.dbus.DirectConnection;
import org.freedesktop.dbus.test.data.TestException;
import org.freedesktop.dbus.test.data.TestRemoteInterface;


public class TestP2PClient {

    public static void main ( String[] args ) throws Exception {
        String address;
        try ( BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream("address"))) ) {
            address = r.readLine();
        }
        catch ( IOException e ) {
            return;
        }
        DirectConnection dc = new DirectConnection(address);
        System.out.println("Connected");
        TestRemoteInterface tri = (TestRemoteInterface) dc.getRemoteObject("/Test");
        System.out.println(tri.getName());
        System.out.println(tri.testfloat(new float[] {
            17.093f, -23f, 0.0f, 31.42f
        }));

        try {
            tri.throwme();
        }
        catch ( TestException Te ) {
            System.out.println("Caught TestException");
        }
        ( (DBus.Peer) tri ).Ping();
        System.out.println( ( (DBus.Introspectable) tri ).Introspect());
        dc.disconnect();
        System.out.println("Disconnected");
    }
}
