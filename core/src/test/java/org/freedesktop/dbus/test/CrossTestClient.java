/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.types.Struct;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;


public class CrossTestClient implements Binding.TestClient, DBusSigHandler<Binding.TestSignals.Triggered> {

    static final Logger log = Logger.getLogger(CrossTestClient.class);

    private DBusConnection conn;

    private boolean gotProperRespose = false;

    private boolean gotProperSignal = false;


    public CrossTestClient ( DBusConnection conn ) {
        this.conn = conn;
    }


    /**
     * @return the gotProperRespose
     */
    public boolean isGotProperRespose () {
        return this.gotProperRespose;
    }


    /**
     * @return the gotProperSignal
     */
    public boolean isGotProperSignal () {
        return this.gotProperSignal;
    }


    /**
     * @return the conn
     */
    public DBusConnection getConn () {
        return this.conn;
    }


    @Override
    public boolean isRemote () {
        return false;
    }


    @Override
    public void handle ( Binding.TestSignals.Triggered t ) {
        if ( new UInt64(21389479283L).equals(t.a) && "/Test".equals(t.getPath()) ) {
            this.gotProperSignal = true;
        }
    }


    @Override
    public void Response ( UInt16 a, double b ) {
        if ( a.equals(new UInt16(15)) && ( b == 12.5 ) ) {
            this.gotProperRespose = true;
        }
    }


    @SuppressWarnings ( "unchecked" )
    public static String collapseArray ( Object array ) {
        if ( null == array )
            return "null";
        if ( array.getClass().isArray() ) {
            String s = "{ ";
            for ( int i = 0; i < Array.getLength(array); i++ )
                s += collapseArray(Array.get(array, i)) + ",";
            s = s.replaceAll(".$", " }");
            return s;
        }
        else if ( array instanceof List ) {
            String s = "{ ";
            for ( Object o : (List<Object>) array )
                s += collapseArray(o) + ",";
            s = s.replaceAll(".$", " }");
            return s;
        }
        else if ( array instanceof Map ) {
            String s = "{ ";
            for ( Object o : ( (Map<Object, Object>) array ).keySet() )
                s += collapseArray(o) + " => " + collapseArray( ( (Map<Object, Object>) array ).get(o)) + ",";
            s = s.replaceAll(".$", " }");
            return s;
        }
        else
            return array.toString();
    }


    public static <T> boolean setCompareLists ( List<T> a, List<T> b ) {
        if ( a.size() != b.size() )
            return false;
        for ( Object v : a )
            if ( !b.contains(v) )
                return false;
        return true;
    }


    @SuppressWarnings ( "unchecked" )
    public static List<Variant<Object>> PrimitizeRecurse ( Object a, Type t ) {
        List<Variant<Object>> vs = new Vector<>();
        if ( t instanceof ParameterizedType ) {
            Class<Object> c = (Class<Object>) ( (ParameterizedType) t ).getRawType();
            if ( List.class.isAssignableFrom(c) ) {
                Object os;
                if ( a instanceof List )
                    os = ( (List<Object>) a ).toArray();
                else
                    os = a;
                Type[] ts = ( (ParameterizedType) t ).getActualTypeArguments();
                for ( int i = 0; i < Array.getLength(os); i++ )
                    vs.addAll(PrimitizeRecurse(Array.get(os, i), ts[ 0 ]));
            }
            else if ( Map.class.isAssignableFrom(c) ) {
                Object[] os = ( (Map<Object, Object>) a ).keySet().toArray();
                Object[] ks = ( (Map<Object, Object>) a ).values().toArray();
                Type[] ts = ( (ParameterizedType) t ).getActualTypeArguments();
                for ( int i = 0; i < ks.length; i++ )
                    vs.addAll(PrimitizeRecurse(ks[ i ], ts[ 0 ]));
                for ( int i = 0; i < os.length; i++ )
                    vs.addAll(PrimitizeRecurse(os[ i ], ts[ 1 ]));
            }
            else if ( Struct.class.isAssignableFrom(c) ) {
                Object[] os = ( (Struct) a ).getParameters();
                Type[] ts = ( (ParameterizedType) t ).getActualTypeArguments();
                for ( int i = 0; i < os.length; i++ )
                    vs.addAll(PrimitizeRecurse(os[ i ], ts[ i ]));

            }
            else if ( Variant.class.isAssignableFrom(c) ) {
                vs.addAll(PrimitizeRecurse( ( (Variant<?>) a ).getValue(), ( (Variant<?>) a ).getType()));
            }
        }
        else if ( Variant.class.isAssignableFrom((Class<?>) t) )
            vs.addAll(PrimitizeRecurse( ( (Variant<?>) a ).getValue(), ( (Variant<?>) a ).getType()));
        else if ( t instanceof Class && ( (Class<?>) t ).isArray() ) {
            Type t2 = ( (Class<?>) t ).getComponentType();
            for ( int i = 0; i < Array.getLength(a); i++ )
                vs.addAll(PrimitizeRecurse(Array.get(a, i), t2));
        }
        else
            vs.add(new Variant<>(a));

        return vs;
    }


    public static List<Variant<Object>> Primitize ( Variant<Object> a ) {
        return PrimitizeRecurse(a.getValue(), a.getType());
    }


    public static void compareArray ( String test, Object a, Object b ) {
        assertEquals(a.getClass(), b.getClass()); // "Incorrect return type"
        boolean pass = false;

        if ( a instanceof Object[] )
            pass = Arrays.equals((Object[]) a, (Object[]) b);
        else if ( a instanceof byte[] )
            pass = Arrays.equals((byte[]) a, (byte[]) b);
        else if ( a instanceof boolean[] )
            pass = Arrays.equals((boolean[]) a, (boolean[]) b);
        else if ( a instanceof int[] )
            pass = Arrays.equals((int[]) a, (int[]) b);
        else if ( a instanceof short[] )
            pass = Arrays.equals((short[]) a, (short[]) b);
        else if ( a instanceof long[] )
            pass = Arrays.equals((long[]) a, (long[]) b);
        else if ( a instanceof double[] )
            pass = Arrays.equals((double[]) a, (double[]) b);

        if ( !pass ) {
            String s = "Incorrect return value; expected ";
            s += collapseArray(a);
            s += " got ";
            s += collapseArray(b);

            fail(s);
        }
    }

}
