/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.types;


import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;


public class DBusMap <K, V> implements Map<K, V> {

    public Object[][] entries;


    public DBusMap ( Object[][] entries ) {
        this.entries = entries;
    }

    class Entry implements Map.Entry<K, V>, Comparable<Entry> {

        private int entry;


        public Entry ( int i ) {
            this.entry = i;
        }


        @SuppressWarnings ( "unchecked" )
        @Override
        public boolean equals ( Object o ) {
            if ( null == o )
                return false;
            if ( o instanceof DBusMap.Entry ) {
                return this.entry == ( (Entry) o ).entry;
            }

            return false;

        }


        @Override
        @SuppressWarnings ( "unchecked" )
        public K getKey () {
            return (K) DBusMap.this.entries[ this.entry ][ 0 ];
        }


        @Override
        @SuppressWarnings ( "unchecked" )
        public V getValue () {
            return (V) DBusMap.this.entries[ this.entry ][ 1 ];
        }


        @Override
        public int hashCode () {
            return DBusMap.this.entries[ this.entry ][ 0 ].hashCode();
        }


        @Override
        public V setValue ( V value ) {
            throw new UnsupportedOperationException();
        }


        @Override
        public int compareTo ( Entry e ) {
            return this.entry - e.entry;
        }
    }


    @Override
    public void clear () {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean containsKey ( Object key ) {
        for ( int i = 0; i < this.entries.length; i++ )
            if ( key == this.entries[ i ][ 0 ] || ( key != null && key.equals(this.entries[ i ][ 0 ]) ) )
                return true;
        return false;
    }


    @Override
    public boolean containsValue ( Object value ) {
        for ( int i = 0; i < this.entries.length; i++ )
            if ( value == this.entries[ i ][ 1 ] || ( value != null && value.equals(this.entries[ i ][ 1 ]) ) )
                return true;
        return false;
    }


    @Override
    public Set<Map.Entry<K, V>> entrySet () {
        Set<Map.Entry<K, V>> s = new TreeSet<>();
        for ( int i = 0; i < this.entries.length; i++ )
            s.add(new Entry(i));
        return s;
    }


    @Override
    @SuppressWarnings ( "unchecked" )
    public V get ( Object key ) {
        for ( int i = 0; i < this.entries.length; i++ )
            if ( key == this.entries[ i ][ 0 ] || ( key != null && key.equals(this.entries[ i ][ 0 ]) ) )
                return (V) this.entries[ i ][ 1 ];
        return null;
    }


    @Override
    public boolean isEmpty () {
        return this.entries.length == 0;
    }


    @Override
    @SuppressWarnings ( "unchecked" )
    public Set<K> keySet () {
        Set<K> s = new TreeSet<>();
        for ( Object[] entry : this.entries )
            s.add((K) entry[ 0 ]);
        return s;
    }


    @Override
    public V put ( K key, V value ) {
        throw new UnsupportedOperationException();
    }


    @Override
    public void putAll ( Map<? extends K, ? extends V> t ) {
        throw new UnsupportedOperationException();
    }


    @Override
    public V remove ( Object key ) {
        throw new UnsupportedOperationException();
    }


    @Override
    public int size () {
        return this.entries.length;
    }


    @Override
    @SuppressWarnings ( "unchecked" )
    public Collection<V> values () {
        List<V> l = new Vector<>();
        for ( Object[] entry : this.entries )
            l.add((V) entry[ 1 ]);
        return l;
    }


    @Override
    public int hashCode () {
        return Arrays.deepHashCode(this.entries);
    }


    @Override
    @SuppressWarnings ( "unchecked" )
    public boolean equals ( Object o ) {
        if ( null == o )
            return false;
        if ( ! ( o instanceof Map ) )
            return false;
        return ( (Map<K, V>) o ).entrySet().equals(entrySet());
    }


    @Override
    public String toString () {
        String s = "{ ";
        for ( int i = 0; i < this.entries.length; i++ )
            s += this.entries[ i ][ 0 ] + " => " + this.entries[ i ][ 1 ] + ",";
        return s.replaceAll(".$", " }");
    }
}
