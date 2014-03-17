/*
   D-Bus Java Viewer
   Copyright (c) 2006 Peter Cox

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.bin.viewer;


import org.freedesktop.dbus.DBus.Introspectable;
import org.freedesktop.dbus.types.UInt32;


/**
 * A summary class for a dbus entry for use in a table model
 * 
 * @author pete
 * @since 10/02/2006
 */
public class DBusEntry {

    private String name;

    private String path;

    private UInt32 user;

    private String owner;

    private Introspectable introspectable;


    /**
     * Assign the name
     * 
     * @param name
     *            The name.
     */
    public void setName ( String name ) {
        this.name = name;
    }


    /**
     * Retrieve the name
     * 
     * @return The name.
     */
    public String getName () {
        return this.name;
    }


    /**
     * Assign the user
     * 
     * @param user
     *            The user.
     */
    public void setUser ( UInt32 user ) {
        this.user = user;
    }


    /**
     * Retrieve the user
     * 
     * @return The user.
     */
    public UInt32 getUser () {
        return this.user;
    }


    /**
     * Assign the owner
     * 
     * @param owner
     *            The owner.
     */
    public void setOwner ( String owner ) {
        this.owner = owner;
    }


    /**
     * Retrieve the owner
     * 
     * @return The owner.
     */
    public String getOwner () {
        return this.owner;
    }


    /**
     * Assign the introspectable
     * 
     * @param introspectable
     *            The introspectable.
     */
    public void setIntrospectable ( Introspectable introspectable ) {
        this.introspectable = introspectable;
    }


    /**
     * Retrieve the introspectable
     * 
     * @return The introspectable.
     */
    public Introspectable getIntrospectable () {
        return this.introspectable;
    }


    /**
     * retrieve the path parameter
     * 
     * @return
     */
    public String getPath () {
        return this.path;
    }


    /**
     * set the path parameter
     * 
     * @param path
     */
    public void setPath ( String path ) {
        this.path = path;
    }

}
