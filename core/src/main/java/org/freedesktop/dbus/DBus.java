/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;


@DBusInterfaceName ( "org.freedesktop.DBus" )
public interface DBus extends DBusInterface {

    public static final int DBUS_NAME_FLAG_ALLOW_REPLACEMENT = 0x01;
    public static final int DBUS_NAME_FLAG_REPLACE_EXISTING = 0x02;
    public static final int DBUS_NAME_FLAG_DO_NOT_QUEUE = 0x04;
    public static final int DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER = 1;
    public static final int DBUS_REQUEST_NAME_REPLY_IN_QUEUE = 2;
    public static final int DBUS_REQUEST_NAME_REPLY_EXISTS = 3;
    public static final int DBUS_REQUEST_NAME_REPLY_ALREADY_OWNER = 4;
    public static final int DBUS_RELEASE_NAME_REPLY_RELEASED = 1;
    public static final int DBUS_RELEASE_NAME_REPLY_NON_EXISTANT = 2;
    public static final int DBUS_RELEASE_NAME_REPLY_NOT_OWNER = 3;
    public static final int DBUS_START_REPLY_SUCCESS = 1;
    public static final int DBUS_START_REPLY_ALREADY_RUNNING = 2;

    /**
     * All DBus Applications should respond to the Ping method on this interface
     */
    @DBusInterfaceName ( "org.freedesktop.DBus.Peer" )
    public interface Peer extends DBusInterface {

        public void Ping ();
    }

    /**
     * Objects can provide introspection data via this interface and method.
     * See the <a href="http://dbus.freedesktop.org/doc/dbus-specification.html#introspection-format">Introspection
     * Format</a>.
     */
    @DBusInterfaceName ( "org.freedesktop.DBus.Introspectable" )
    public interface Introspectable extends DBusInterface {

        /**
         * @return The XML introspection data for this object
         */
        public String Introspect ();
    }

    /**
     * A standard properties interface.
     */
    @DBusInterfaceName ( "org.freedesktop.DBus.Properties" )
    public interface Properties extends DBusInterface {

        /**
         * Get the value for the given property.
         * 
         * @param interface_name
         *            The interface this property is associated with.
         * @param property_name
         *            The name of the property.
         * @return The value of the property (may be any valid DBus type).
         */
        public <A> A Get ( String interface_name, String property_name );


        /**
         * Set the value for the given property.
         * 
         * @param interface_name
         *            The interface this property is associated with.
         * @param property_name
         *            The name of the property.
         * @param value
         *            The new value of the property (may be any valid DBus type).
         */
        public <A> void Set ( String interface_name, String property_name, A value );


        /**
         * Get all properties and values.
         * 
         * @param interface_name
         *            The interface the properties is associated with.
         * @return The properties mapped to their values.
         */
        public Map<String, Variant<?>> GetAll ( String interface_name );
    }

    /**
     * Messages generated locally in the application.
     */
    @DBusInterfaceName ( "org.freedesktop.DBus.Local" )
    public interface Local extends DBusInterface {

        public class Disconnected extends DBusSignal {

            public Disconnected ( String path ) throws DBusException {
                super(path);
            }
        }
    }

    @DBusInterfaceName ( "org.freedesktop.DBus.ObjectManager" )
    public interface ObjectManager extends DBusInterface {

        public static class InterfacesAdded extends DBusSignal {

            public final DBusInterface object_path;
            public final Map<String, Map<String, Variant<?>>> interfaces_and_properties;


            public InterfacesAdded ( String path, DBusInterface object_path, Map<String, Map<String, Variant<?>>> interfaces_and_properties )
                    throws DBusException {
                super(path, object_path, interfaces_and_properties);
                this.object_path = object_path;
                this.interfaces_and_properties = interfaces_and_properties;
            }
        }

        public static class InterfacesRemoved extends DBusSignal {

            public final DBusInterface object_path;
            public final List<String> interfaces;


            public InterfacesRemoved ( String path, DBusInterface object_path, List<String> interfaces ) throws DBusException {
                super(path, object_path, interfaces);
                this.object_path = object_path;
                this.interfaces = interfaces;
            }
        }


        public Map<DBusInterface, Map<String, Map<String, Variant<?>>>> GetManagedObjects ();

    }


    /**
     * Initial message to register ourselves on the Bus.
     * 
     * @return The unique name of this connection to the Bus.
     */
    public String Hello ();


    /**
     * Lists all connected names on the Bus.
     * 
     * @return An array of all connected names.
     */
    public String[] ListNames ();


    /**
     * Determine if a name has an owner.
     * 
     * @param name
     *            The name to query.
     * @return true if the name has an owner.
     */
    public boolean NameHasOwner ( String name );


    /**
     * Get the connection unique name that owns the given name.
     * 
     * @param name
     *            The name to query.
     * @return The connection which owns the name.
     */
    public String GetNameOwner ( String name );


    /**
     * Get the Unix UID that owns a connection name.
     * 
     * @param connection_name
     *            The connection name.
     * @return The Unix UID that owns it.
     */
    public UInt32 GetConnectionUnixUser ( String connection_name );


    /**
     * Start a service. If the given service is not provided
     * by any application, it will be started according to the .service file
     * for that service.
     * 
     * @param name
     *            The service name to start.
     * @param flags
     *            Unused.
     * @return DBUS_START_REPLY constants.
     */
    public UInt32 StartServiceByName ( String name, UInt32 flags );


    /**
     * Request a name on the bus.
     * 
     * @param name
     *            The name to request.
     * @param flags
     *            DBUS_NAME flags.
     * @return DBUS_REQUEST_NAME_REPLY constants.
     */
    public UInt32 RequestName ( String name, UInt32 flags );


    /**
     * Release a name on the bus.
     * 
     * @param name
     *            The name to release.
     * @return DBUS_RELEASE_NAME_REPLY constants.
     */
    public UInt32 ReleaseName ( String name );


    /**
     * Add a match rule.
     * Will cause you to receive messages that aren't directed to you which
     * match this rule.
     * 
     * @param matchrule
     *            The Match rule as a string. Format Undocumented.
     */
    public void AddMatch ( String matchrule ) throws Error.MatchRuleInvalid;


    /**
     * Remove a match rule.
     * Will cause you to stop receiving messages that aren't directed to you which
     * match this rule.
     * 
     * @param matchrule
     *            The Match rule as a string. Format Undocumented.
     */
    public void RemoveMatch ( String matchrule ) throws Error.MatchRuleInvalid;


    /**
     * List the connections currently queued for a name.
     * 
     * @param name
     *            The name to query
     * @return A list of unique connection IDs.
     */
    public String[] ListQueuedOwners ( String name );


    /**
     * Returns the proccess ID associated with a connection.
     * 
     * @param connection_name
     *            The name of the connection
     * @return The PID of the connection.
     */
    public UInt32 GetConnectionUnixProcessID ( String connection_name );


    /**
     * Does something undocumented.
     */
    public Byte[] GetConnectionSELinuxSecurityContext ( String a );


    /**
     * Does something undocumented.
     */
    public void ReloadConfig ();

    /**
     * Signal sent when the owner of a name changes
     */
    public class NameOwnerChanged extends DBusSignal {

        public final String name;
        public final String old_owner;
        public final String new_owner;


        public NameOwnerChanged ( String path, String name, String old_owner, String new_owner ) throws DBusException {
            super(path, new Object[] {
                name, old_owner, new_owner
            });
            this.name = name;
            this.old_owner = old_owner;
            this.new_owner = new_owner;
        }
    }

    /**
     * Signal sent to a connection when it loses a name
     */
    public class NameLost extends DBusSignal {

        public final String name;


        public NameLost ( String path, String name ) throws DBusException {
            super(path, name);
            this.name = name;
        }
    }

    /**
     * Signal sent to a connection when it aquires a name
     */
    public class NameAcquired extends DBusSignal {

        public final String name;


        public NameAcquired ( String path, String name ) throws DBusException {
            super(path, name);
            this.name = name;
        }
    }

    /**
     * Contains standard errors that can be thrown from methods.
     */
    public interface Error {

        /**
         * Thrown if the method called was unknown on the remote object
         */
        @SuppressWarnings ( "serial" )
        public class UnknownMethod extends DBusExecutionException {

            public UnknownMethod ( String message ) {
                super(message);
            }
        }

        /**
         * Thrown if the object was unknown on a remote connection
         */
        @SuppressWarnings ( "serial" )
        public class UnknownObject extends DBusExecutionException {

            public UnknownObject ( String message ) {
                super(message);
            }
        }

        /**
         * Thrown if the requested service was not available
         */
        @SuppressWarnings ( "serial" )
        public class ServiceUnknown extends DBusExecutionException {

            public ServiceUnknown ( String message ) {
                super(message);
            }
        }

        /**
         * Thrown if the match rule is invalid
         */
        @SuppressWarnings ( "serial" )
        public class MatchRuleInvalid extends DBusExecutionException {

            public MatchRuleInvalid ( String message ) {
                super(message);
            }
        }

        /**
         * Thrown if there is no reply to a method call
         */
        @SuppressWarnings ( "serial" )
        public class NoReply extends DBusExecutionException {

            public NoReply ( String message ) {
                super(message);
            }
        }

        /**
         * Thrown if a message is denied due to a security policy
         */
        @SuppressWarnings ( "serial" )
        public class AccessDenied extends DBusExecutionException {

            public AccessDenied ( String message ) {
                super(message);
            }
        }
    }

    /**
     * Description of the interface or method, returned in the introspection data
     */
    @Retention ( RetentionPolicy.RUNTIME )
    public @interface Description {

        String value();
    }

    /**
     * Indicates that a DBus interface or method is deprecated
     */
    @Retention ( RetentionPolicy.RUNTIME )
    public @interface Deprecated {}

    /**
     * Contains method-specific annotations
     */
    public interface Method {

        /**
         * Methods annotated with this do not send a reply
         */
        @Target ( ElementType.METHOD )
        @Retention ( RetentionPolicy.RUNTIME )
        public @interface NoReply {}

        /**
         * Give an error that the method can return
         */
        @Target ( ElementType.METHOD )
        @Retention ( RetentionPolicy.RUNTIME )
        public @interface Error {

            String value();
        }
    }

    /**
     * Contains GLib-specific annotations
     */
    public interface GLib {

        /**
         * Define a C symbol to map to this method. Used by GLib only
         */
        @Target ( ElementType.METHOD )
        @Retention ( RetentionPolicy.RUNTIME )
        public @interface CSymbol {

            String value();
        }
    }
}
