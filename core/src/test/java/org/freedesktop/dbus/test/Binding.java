/**
 * © 2014 AgNO3 Gmbh & Co. KG
 * All right reserved.
 * 
 * Created: 06.03.2014 by mbechler
 */
package org.freedesktop.dbus.test;


import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.DBus.Description;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.Struct;
import org.freedesktop.dbus.types.Tuple;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;


/**
 * Contains Binding-test interfaces
 */
public interface Binding {

    public interface SingleTests extends DBusInterface {

        @Description ( "Returns the sum of the values in the input list" )
        public UInt32 Sum ( byte[] a );
    }

    public interface TestClient extends DBusInterface {

        @Description ( "when the trigger signal is received, this method should be called on the sending process/object." )
        public void Response ( UInt16 a, double b );

        @Description ( "Causes a callback" )
        public static class Trigger extends DBusSignal {

            public final UInt16 a;
            public final double b;


            public Trigger ( String path, UInt16 a, double b ) throws DBusException {
                super(path, a, b);
                this.a = a;
                this.b = b;
            }
        }

    }

    public interface Tests extends DBusInterface {

        @Description ( "Returns whatever it is passed" )
        public <T> Variant<T> Identity ( Variant<T> input );


        @Description ( "Returns whatever it is passed" )
        public byte IdentityByte ( byte input );


        @Description ( "Returns whatever it is passed" )
        public boolean IdentityBool ( boolean input );


        @Description ( "Returns whatever it is passed" )
        public short IdentityInt16 ( short input );


        @Description ( "Returns whatever it is passed" )
        public UInt16 IdentityUInt16 ( UInt16 input );


        @Description ( "Returns whatever it is passed" )
        public int IdentityInt32 ( int input );


        @Description ( "Returns whatever it is passed" )
        public UInt32 IdentityUInt32 ( UInt32 input );


        @Description ( "Returns whatever it is passed" )
        public long IdentityInt64 ( long input );


        @Description ( "Returns whatever it is passed" )
        public UInt64 IdentityUInt64 ( UInt64 input );


        @Description ( "Returns whatever it is passed" )
        public double IdentityDouble ( double input );


        @Description ( "Returns whatever it is passed" )
        public String IdentityString ( String input );


        @Description ( "Returns whatever it is passed" )
        public <T> Variant<T>[] IdentityArray ( Variant<T>[] input );


        @Description ( "Returns whatever it is passed" )
        public byte[] IdentityByteArray ( byte[] input );


        @Description ( "Returns whatever it is passed" )
        public boolean[] IdentityBoolArray ( boolean[] input );


        @Description ( "Returns whatever it is passed" )
        public short[] IdentityInt16Array ( short[] input );


        @Description ( "Returns whatever it is passed" )
        public UInt16[] IdentityUInt16Array ( UInt16[] input );


        @Description ( "Returns whatever it is passed" )
        public int[] IdentityInt32Array ( int[] input );


        @Description ( "Returns whatever it is passed" )
        public UInt32[] IdentityUInt32Array ( UInt32[] input );


        @Description ( "Returns whatever it is passed" )
        public long[] IdentityInt64Array ( long[] input );


        @Description ( "Returns whatever it is passed" )
        public UInt64[] IdentityUInt64Array ( UInt64[] input );


        @Description ( "Returns whatever it is passed" )
        public double[] IdentityDoubleArray ( double[] input );


        @Description ( "Returns whatever it is passed" )
        public String[] IdentityStringArray ( String[] input );


        @Description ( "Returns the sum of the values in the input list" )
        public long Sum ( int[] a );


        @Description ( "Given a map of A => B, should return a map of B => a list of all the As which mapped to B" )
        public Map<String, List<String>> InvertMapping ( Map<String, String> a );


        @Description ( "This method returns the contents of a struct as separate values" )
        public Binding.Triplet<String, UInt32, Short> DeStruct ( Binding.TestStruct a );


        @Description ( "Given any compound type as a variant, return all the primitive types recursively contained within as an array of variants" )
        public List<Variant<Object>> Primitize ( Variant<Object> a );


        @Description ( "inverts it's input" )
        public boolean Invert ( boolean a );


        @Description ( "triggers sending of a signal from the supplied object with the given parameter" )
        public void Trigger ( String a, UInt64 b );


        @Description ( "Causes the server to exit" )
        public void Exit ();
    }

    public interface TestSignals extends DBusInterface {

        @Description ( "Sent in response to a method call" )
        public static class Triggered extends DBusSignal {

            public final UInt64 a;


            public Triggered ( String path, UInt64 a ) throws DBusException {
                super(path, a);
                this.a = a;
            }
        }
    }

    public final class Triplet <A, B, C> extends Tuple {

        @Position ( 0 )
        public final A a;
        @Position ( 1 )
        public final B b;
        @Position ( 2 )
        public final C c;


        public Triplet ( A a, B b, C c ) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    public final class TestStruct extends Struct {

        @Position ( 0 )
        public final String a;
        @Position ( 1 )
        public final UInt32 b;
        @Position ( 2 )
        public final Short c;


        public TestStruct ( String a, UInt32 b, Short c ) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }
}