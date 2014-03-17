/**
 * © 2014 AgNO3 Gmbh & Co. KG
 * All right reserved.
 * 
 * Created: 08.03.2014 by mbechler
 */
package org.freedesktop.dbus.test;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.DBus;
import org.freedesktop.dbus.DBus.Introspectable;
import org.freedesktop.dbus.DBus.Peer;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.types.DBusMapType;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author mbechler
 * 
 */
public class CrossTest {

    /**
     * 
     */
    private static final String SERVER_NAME = "org.freedesktop.dbus.test.Binding.TestServer";

    private static final Logger log = Logger.getLogger(CrossTest.class);
    private static CrossTestClient ctc;
    private static Binding.Tests tests;
    private static Binding.SingleTests singletests;
    private static Peer peer;
    private static Introspectable intro;
    private static Introspectable rootintro;
    private static DBusConnection c;
    private static DBusConnection sc;
    private static CrossTestServer cts;

    private static Random r;


    @BeforeClass
    public static void setup () throws DBusException, IllegalArgumentException {
        sc = DBusConnection.getConnection(DBusConnection.SESSION);
        sc.requestBusName(SERVER_NAME);
        cts = new CrossTestServer(sc);
        sc.addSigHandler(Binding.TestClient.Trigger.class, cts);
        sc.exportObject("/CrossTest", cts);

        c = DBusConnection.getConnection(DBusConnection.SESSION);
        ctc = new CrossTestClient(c);
        c.exportObject("/TestClient", ctc);
        c.addSigHandler(Binding.TestSignals.Triggered.class, ctc);

        tests = c.getRemoteObject(SERVER_NAME, "/CrossTest", Binding.Tests.class);
        singletests = c.getRemoteObject(SERVER_NAME, "/CrossTest", Binding.SingleTests.class);
        peer = c.getRemoteObject(SERVER_NAME, "/CrossTest", DBus.Peer.class);
        intro = c.getRemoteObject(SERVER_NAME, "/CrossTest", DBus.Introspectable.class);
        rootintro = c.getRemoteObject(SERVER_NAME, "/", DBus.Introspectable.class);

        r = new Random();
    }


    @AfterClass
    public static void teardown () {
        c.disconnect();
        cts.run = false;
        sc.disconnect();
    }


    @SuppressWarnings ( "unchecked" )
    public void test ( Class<? extends DBusInterface> iface, Object proxy, String method, Object rv, Object... parameters )
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method[] ms = iface.getMethods();
        Method m = null;
        for ( Method t : ms ) {
            if ( t.getName().equals(method) )
                m = t;
        }

        assertNotNull(m);
        Object o = m.invoke(proxy, parameters);

        String msg = "Incorrect return value; sent ( ";
        if ( null != parameters )
            for ( Object po : parameters )
                if ( null != po )
                    msg += CrossTestClient.collapseArray(po) + ",";
        msg = msg.replaceAll(".$", ");");
        msg += " expected " + CrossTestClient.collapseArray(rv) + " got " + CrossTestClient.collapseArray(o);

        if ( null != rv && rv.getClass().isArray() ) {
            CrossTestClient.compareArray(iface.getName() + "." + method, rv, o);
        }
        else if ( rv instanceof Map ) {
            if ( o instanceof Map ) {
                Map<Object, Object> a = (Map<Object, Object>) o;
                Map<Object, Object> b = (Map<Object, Object>) rv;
                if ( a.keySet().size() != b.keySet().size() ) {
                    fail(iface.getName() + "." + method + ":" + msg);
                }
                else
                    for ( Object k : a.keySet() )
                        if ( a.get(k) instanceof List ) {
                            if ( b.get(k) instanceof List )
                                if ( CrossTestClient.setCompareLists((List<Object>) a.get(k), (List<Object>) b.get(k)) )
                                    ;
                                else
                                    fail(iface.getName() + "." + method + ":" + msg);
                            else
                                fail(iface.getName() + "." + method + ":" + msg);
                        }
                        else if ( !a.get(k).equals(b.get(k)) ) {
                            fail(iface.getName() + "." + method + ":" + msg);
                        }
            }
            else
                fail(iface.getName() + "." + method + ":" + msg);
        }
        else {
            if ( ! ( o == rv || ( o != null && o.equals(rv) ) ) )
                fail(iface.getName() + "." + method + ":" + msg);
        }
    }


    @Test
    public void testPing () throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        this.test(DBus.Peer.class, peer, "Ping", null);
    }


    @Test
    public void testIntrospect () {
        if ( !intro.Introspect().startsWith("<!DOCTYPE") )
            fail("org.freedesktop.DBus.Introspectable.Introspect: Didn't get valid xml data back when introspecting /Test");

        if ( !rootintro.Introspect().startsWith("<!DOCTYPE") )
            fail("org.freedesktop.DBus.Introspectable.Introspect: Didn't get valid xml data back when introspecting /: ");
    }


    @Test
    public void testTrigger () throws IllegalAccessException, InvocationTargetException {
        assertFalse(ctc.isGotProperSignal());
        this.test(Binding.Tests.class, tests, "Trigger", null, "/Test", new UInt64(21389479283L));
        try {
            Thread.sleep(500);
        }
        catch ( InterruptedException Ie ) {}
        assertTrue(ctc.isGotProperSignal());
    }


    @Test
    public void testComplex () throws IllegalAccessException, InvocationTargetException {
        Map<String, String> in = new HashMap<>();
        Map<String, List<String>> out = new HashMap<>();
        this.test(Binding.Tests.class, tests, "InvertMapping", out, in);

        in.put("hi", "there");
        in.put("to", "there");
        in.put("from", "here");
        in.put("in", "out");
        List<String> l = new Vector<>();
        l.add("hi");
        l.add("to");
        out.put("there", l);
        l = new Vector<>();
        l.add("from");
        out.put("here", l);
        l = new Vector<>();
        l.add("in");
        out.put("out", l);
        this.test(Binding.Tests.class, tests, "InvertMapping", out, in);

        this.primitizeTest(tests, new Integer(1));
        this.primitizeTest(tests, new Variant<>(new Variant<>(new Variant<>(new Variant<>("Hi")))));
        this.primitizeTest(tests, new Variant<>(in, new DBusMapType(String.class, String.class)));
    }


    @Test
    public void testSignal () {
        assertFalse(ctc.isGotProperRespose());
        try {
            ctc.getConn().sendSignal(new Binding.TestClient.Trigger("/Test", new UInt16(15), 12.5));
        }
        catch ( DBusException DBe ) {
            log.warn(DBe);
            throw new DBusExecutionException(DBe.getMessage());
        }

        try {
            Thread.sleep(500);
        }
        catch ( InterruptedException Ie ) {}

        assertTrue(ctc.isGotProperRespose());
    }


    @Test
    public void testStruct () throws IllegalAccessException, InvocationTargetException {
        this.test(Binding.Tests.class, tests, "DeStruct", new Binding.Triplet<>("hi", new UInt32(12), new Short((short) 99)), new Binding.TestStruct(
            "hi",
            new UInt32(12),
            new Short((short) 99)));
    }


    @Test
    public void testSum () throws IllegalAccessException, InvocationTargetException {
        int i;
        int[] is = new int[0];
        this.test(Binding.Tests.class, tests, "Sum", 0L, is);
        int len = ( r.nextInt() % 100 ) + 15;
        len = ( len < 0 ? -len : len ) + 15;
        is = new int[len];
        long result = 0;
        for ( i = 0; i < len; i++ ) {
            is[ i ] = r.nextInt();
            result += is[ i ];
        }
        this.test(Binding.Tests.class, tests, "Sum", result, is);

        byte[] bs = new byte[0];
        this.test(Binding.SingleTests.class, singletests, "Sum", new UInt32(0), bs);
        len = ( r.nextInt() % 100 );
        len = ( len < 0 ? -len : len ) + 15;
        bs = new byte[len];
        int res = 0;
        for ( i = 0; i < len; i++ ) {
            bs[ i ] = (byte) r.nextInt();
            res += ( bs[ i ] < 0 ? bs[ i ] + 256 : bs[ i ] );
        }
        this.test(Binding.SingleTests.class, singletests, "Sum", new UInt32(res % ( UInt32.MAX_VALUE + 1 )), bs);
    }


    @Test
    public void testByte () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityByte", (byte) 0, (byte) 0);
        this.test(Binding.Tests.class, tests, "IdentityByte", (byte) 1, (byte) 1);
        this.test(Binding.Tests.class, tests, "IdentityByte", (byte) -1, (byte) -1);
        this.test(Binding.Tests.class, tests, "IdentityByte", Byte.MAX_VALUE, Byte.MAX_VALUE);
        this.test(Binding.Tests.class, tests, "IdentityByte", Byte.MIN_VALUE, Byte.MIN_VALUE);
        i = r.nextInt();
        this.test(Binding.Tests.class, tests, "IdentityByte", (byte) i, (byte) i);
    }


    @Test
    public void testINT16 () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityInt16", (short) 0, (short) 0);
        this.test(Binding.Tests.class, tests, "IdentityInt16", (short) 1, (short) 1);
        this.test(Binding.Tests.class, tests, "IdentityInt16", (short) -1, (short) -1);
        this.test(Binding.Tests.class, tests, "IdentityInt16", Short.MAX_VALUE, Short.MAX_VALUE);
        this.test(Binding.Tests.class, tests, "IdentityInt16", Short.MIN_VALUE, Short.MIN_VALUE);
        i = r.nextInt();
        this.test(Binding.Tests.class, tests, "IdentityInt16", (short) i, (short) i);
    }


    @Test
    public void testINT32 () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityInt32", 0, 0);
        this.test(Binding.Tests.class, tests, "IdentityInt32", 1, 1);
        this.test(Binding.Tests.class, tests, "IdentityInt32", -1, -1);
        this.test(Binding.Tests.class, tests, "IdentityInt32", Integer.MAX_VALUE, Integer.MAX_VALUE);
        this.test(Binding.Tests.class, tests, "IdentityInt32", Integer.MIN_VALUE, Integer.MIN_VALUE);
        i = r.nextInt();
        this.test(Binding.Tests.class, tests, "IdentityInt32", i, i);
    }


    @Test
    public void testINT64 () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityInt64", (long) 0, (long) 0);
        this.test(Binding.Tests.class, tests, "IdentityInt64", (long) 1, (long) 1);
        this.test(Binding.Tests.class, tests, "IdentityInt64", (long) -1, (long) -1);
        this.test(Binding.Tests.class, tests, "IdentityInt64", Long.MAX_VALUE, Long.MAX_VALUE);
        this.test(Binding.Tests.class, tests, "IdentityInt64", Long.MIN_VALUE, Long.MIN_VALUE);
        i = r.nextInt();
        this.test(Binding.Tests.class, tests, "IdentityInt64", (long) i, (long) i);
    }


    @Test
    public void testUINT16 () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityUInt16", new UInt16(0), new UInt16(0));
        this.test(Binding.Tests.class, tests, "IdentityUInt16", new UInt16(1), new UInt16(1));
        this.test(Binding.Tests.class, tests, "IdentityUInt16", new UInt16(UInt16.MAX_VALUE), new UInt16(UInt16.MAX_VALUE));
        this.test(Binding.Tests.class, tests, "IdentityUInt16", new UInt16(UInt16.MIN_VALUE), new UInt16(UInt16.MIN_VALUE));
        i = r.nextInt();
        i = i > 0 ? i : -i;
        this.test(Binding.Tests.class, tests, "IdentityUInt16", new UInt16(i % UInt16.MAX_VALUE), new UInt16(i % UInt16.MAX_VALUE));
    }


    @Test
    public void testUINT32 () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityUInt32", new UInt32(0), new UInt32(0));
        this.test(Binding.Tests.class, tests, "IdentityUInt32", new UInt32(1), new UInt32(1));
        this.test(Binding.Tests.class, tests, "IdentityUInt32", new UInt32(UInt32.MAX_VALUE), new UInt32(UInt32.MAX_VALUE));
        this.test(Binding.Tests.class, tests, "IdentityUInt32", new UInt32(UInt32.MIN_VALUE), new UInt32(UInt32.MIN_VALUE));
        i = r.nextInt();
        i = i > 0 ? i : -i;
        this.test(Binding.Tests.class, tests, "IdentityUInt32", new UInt32(i % UInt32.MAX_VALUE), new UInt32(i % UInt32.MAX_VALUE));
    }


    @Test
    public void testUINT64 () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityUInt64", new UInt64(0), new UInt64(0));
        this.test(Binding.Tests.class, tests, "IdentityUInt64", new UInt64(1), new UInt64(1));
        this.test(Binding.Tests.class, tests, "IdentityUInt64", new UInt64(UInt64.MAX_LONG_VALUE), new UInt64(UInt64.MAX_LONG_VALUE));
        this.test(Binding.Tests.class, tests, "IdentityUInt64", new UInt64(UInt64.MAX_BIG_VALUE), new UInt64(UInt64.MAX_BIG_VALUE));
        this.test(Binding.Tests.class, tests, "IdentityUInt64", new UInt64(UInt64.MIN_VALUE), new UInt64(UInt64.MIN_VALUE));
        i = r.nextInt();
        i = i > 0 ? i : -i;
        this.test(Binding.Tests.class, tests, "IdentityUInt64", new UInt64(i % UInt64.MAX_LONG_VALUE), new UInt64(i % UInt64.MAX_LONG_VALUE));
    }


    @Test
    public void testDouble () throws IllegalAccessException, InvocationTargetException {
        int i;
        this.test(Binding.Tests.class, tests, "IdentityDouble", 0.0, 0.0);
        this.test(Binding.Tests.class, tests, "IdentityDouble", 1.0, 1.0);
        this.test(Binding.Tests.class, tests, "IdentityDouble", -1.0, -1.0);
        this.test(Binding.Tests.class, tests, "IdentityDouble", Double.MAX_VALUE, Double.MAX_VALUE);
        this.test(Binding.Tests.class, tests, "IdentityDouble", Double.MIN_VALUE, Double.MIN_VALUE);
        i = r.nextInt();
        this.test(Binding.Tests.class, tests, "IdentityDouble", (double) i, (double) i);
    }


    @Test
    public void testStrings () throws IllegalAccessException, InvocationTargetException {
        this.test(Binding.Tests.class, tests, "IdentityString", "", "");
        this.test(
            Binding.Tests.class,
            tests,
            "IdentityString",
            "The Quick Brown Fox Jumped Over The Lazy Dog",
            "The Quick Brown Fox Jumped Over The Lazy Dog");
        this.test(Binding.Tests.class, tests, "IdentityString", "ひらがなゲーム - かなぶん", "ひらがなゲーム - かなぶん");
    }


    @Test
    public void testArrays () throws IllegalAccessException, InvocationTargetException {
        this.testArray(Binding.Tests.class, tests, "IdentityBoolArray", Boolean.TYPE, null);
        this.testArray(Binding.Tests.class, tests, "IdentityByteArray", Byte.TYPE, null);
        this.testArray(Binding.Tests.class, tests, "IdentityInt16Array", Short.TYPE, null);
        this.testArray(Binding.Tests.class, tests, "IdentityInt32Array", Integer.TYPE, null);
        this.testArray(Binding.Tests.class, tests, "IdentityInt64Array", Long.TYPE, null);
        this.testArray(Binding.Tests.class, tests, "IdentityDoubleArray", Double.TYPE, null);

        this.testArray(Binding.Tests.class, tests, "IdentityArray", Variant.class, new Variant<>("aoeu"));
        this.testArray(Binding.Tests.class, tests, "IdentityUInt16Array", UInt16.class, new UInt16(12));
        this.testArray(Binding.Tests.class, tests, "IdentityUInt32Array", UInt32.class, new UInt32(190));
        this.testArray(Binding.Tests.class, tests, "IdentityUInt64Array", UInt64.class, new UInt64(103948));
        this.testArray(Binding.Tests.class, tests, "IdentityStringArray", String.class, "asdf");
    }


    @Test
    public void testIdentityBool () throws IllegalAccessException, InvocationTargetException {
        this.test(Binding.Tests.class, tests, "IdentityBool", false, false);
        this.test(Binding.Tests.class, tests, "IdentityBool", true, true);

        this.test(Binding.Tests.class, tests, "Invert", false, true);
        this.test(Binding.Tests.class, tests, "Invert", true, false);
    }


    @Test
    public void testIdentity () throws IllegalAccessException, InvocationTargetException {
        this.test(Binding.Tests.class, tests, "Identity", new Variant<>(new Integer(1)), new Variant<>(new Integer(1)));
        this.test(Binding.Tests.class, tests, "Identity", new Variant<>("Hello"), new Variant<>("Hello"));
    }


    public void testArray ( Class<? extends DBusInterface> iface, Object proxy, String method, Class<? extends Object> arrayType, Object content )
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Object array = Array.newInstance(arrayType, 0);
        this.test(iface, proxy, method, array, array);
        int l = ( r.nextInt() % 100 );
        array = Array.newInstance(arrayType, ( l < 0 ? -l : l ) + 15);
        if ( null != content )
            Arrays.fill((Object[]) array, content);
        this.test(iface, proxy, method, array, array);
    }


    public void primitizeTest ( Binding.Tests t, Object input ) {
        Variant<Object> in = new Variant<>(input);
        List<Variant<Object>> vs = CrossTestClient.Primitize(in);
        List<Variant<Object>> res;

        try {

            res = t.Primitize(in);
            if ( !CrossTestClient.setCompareLists(res, vs) ) {
                fail("Wrong Return Value; expected " + CrossTestClient.collapseArray(vs) + " got " + CrossTestClient.collapseArray(res));
            }

        }
        catch ( Exception e ) {
            log.warn(e);
            fail("Exception occurred during test: (" + e.getClass().getName() + ") " + e.getMessage());
        }
    }

}
