/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.test;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.freedesktop.dbus.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;


public class CrossTestServer implements Binding.Tests, Binding.SingleTests, DBusSigHandler<Binding.TestClient.Trigger> {

    private DBusConnection conn;
    boolean run = true;


    public CrossTestServer ( DBusConnection conn ) {
        this.conn = conn;
    }


    @Override
    public boolean isRemote () {
        return false;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public <T> Variant<T> Identity ( Variant<T> input ) {
        return new Variant<>(input.getValue());
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public byte IdentityByte ( byte input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public boolean IdentityBool ( boolean input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public short IdentityInt16 ( short input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public UInt16 IdentityUInt16 ( UInt16 input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public int IdentityInt32 ( int input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public UInt32 IdentityUInt32 ( UInt32 input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public long IdentityInt64 ( long input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public UInt64 IdentityUInt64 ( UInt64 input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public double IdentityDouble ( double input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public String IdentityString ( String input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public <T> Variant<T>[] IdentityArray ( Variant<T>[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public byte[] IdentityByteArray ( byte[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public boolean[] IdentityBoolArray ( boolean[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public short[] IdentityInt16Array ( short[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public UInt16[] IdentityUInt16Array ( UInt16[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public int[] IdentityInt32Array ( int[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public UInt32[] IdentityUInt32Array ( UInt32[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public long[] IdentityInt64Array ( long[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public UInt64[] IdentityUInt64Array ( UInt64[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public double[] IdentityDoubleArray ( double[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns whatever it is passed" )
    public String[] IdentityStringArray ( String[] input ) {
        return input;
    }


    @Override
    @DBus.Description ( "Returns the sum of the values in the input list" )
    public long Sum ( int[] a ) {
        long sum = 0;
        for ( int b : a )
            sum += b;
        return sum;
    }


    @Override
    @DBus.Description ( "Returns the sum of the values in the input list" )
    public UInt32 Sum ( byte[] a ) {
        int sum = 0;
        for ( byte b : a )
            sum += ( b < 0 ? b + 256 : b );
        return new UInt32(sum % ( UInt32.MAX_VALUE + 1 ));
    }


    @Override
    @DBus.Description ( "Given a map of A => B, should return a map of B => a list of all the As which mapped to B" )
    public Map<String, List<String>> InvertMapping ( Map<String, String> a ) {
        HashMap<String, List<String>> m = new HashMap<>();
        for ( String s : a.keySet() ) {
            String b = a.get(s);
            List<String> l = m.get(b);
            if ( null == l ) {
                l = new Vector<>();
                m.put(b, l);
            }
            l.add(s);
        }
        return m;
    }


    @Override
    @DBus.Description ( "This method returns the contents of a struct as separate values" )
    public Binding.Triplet<String, UInt32, Short> DeStruct ( Binding.TestStruct a ) {
        return new Binding.Triplet<>(a.a, a.b, a.c);
    }


    @Override
    @DBus.Description ( "Given any compound type as a variant, return all the primitive types recursively contained within as an array of variants" )
    public List<Variant<Object>> Primitize ( Variant<Object> a ) {
        return CrossTestClient.PrimitizeRecurse(a.getValue(), a.getType());
    }


    @Override
    @DBus.Description ( "inverts it's input" )
    public boolean Invert ( boolean a ) {
        return !a;
    }


    @Override
    @DBus.Description ( "triggers sending of a signal from the supplied object with the given parameter" )
    public void Trigger ( String a, UInt64 b ) {
        try {
            this.conn.sendSignal(new Binding.TestSignals.Triggered(a, b));
        }
        catch ( DBusException DBe ) {
            throw new DBusExecutionException(DBe.getMessage());
        }
    }


    @Override
    public void Exit () {
        this.run = false;
        synchronized ( this ) {
            notifyAll();
        }
    }


    @Override
    public void handle ( Binding.TestClient.Trigger t ) {
        try {
            Binding.TestClient cb = this.conn.getRemoteObject(t.getSource(), "/TestClient", Binding.TestClient.class);
            cb.Response(t.a, t.b);
        }
        catch ( DBusException DBe ) {
            throw new DBusExecutionException(DBe.getMessage());
        }
    }

}
