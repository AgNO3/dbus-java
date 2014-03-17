/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.test;


import org.apache.log4j.Logger;
import org.freedesktop.dbus.BusAddress;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Message;
import org.freedesktop.dbus.MethodCall;
import org.freedesktop.dbus.Transport;
import org.junit.Test;


public class TestLowLevel {

    private static final Logger log = Logger.getLogger(TestLowLevel.class);


    @Test
    public void lowLevelTest () throws Exception {
        String addr = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        log.info(addr);
        BusAddress address = new BusAddress(addr);
        log.info(address);

        Transport conn = new Transport(address);

        Message m = new MethodCall("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "Hello", (byte) 0, null);
        conn.mout.writeMessage(m);
        log.info("read 1");
        m = conn.min.readMessage();
        log.info(m.getClass());
        log.info(m);
        log.info("read 2");
        m = conn.min.readMessage();
        log.info(m.getClass());
        log.info(m);
        log.info("read 3");
        // m = conn.min.readMessage();
        log.info("" + m);
        m = new MethodCall("org.freedesktop.DBus", "/", null, "Hello", (byte) 0, null);
        conn.mout.writeMessage(m);
        log.info("read 4");
        m = conn.min.readMessage();
        log.info(m);

        m = new MethodCall("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "RequestName", (byte) 0, "su", "org.testname", 0);
        conn.mout.writeMessage(m);
        log.info("read 5");
        m = conn.min.readMessage();
        log.info(m);
        m = new DBusSignal(null, "/foo", "org.foo", "Foo", null);
        conn.mout.writeMessage(m);
        log.info("read 6");
        m = conn.min.readMessage();
        log.info(m);
        conn.disconnect();
    }
}
