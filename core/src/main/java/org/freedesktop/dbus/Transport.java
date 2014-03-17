/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.SocketCredentials;


public class Transport {

    static final Logger log = Logger.getLogger(Transport.class);

    public static class SASL {

        public static class Command {

            private int command;
            private int mechs;
            private String data;
            private String response;


            public Command () {}


            public Command ( String s ) throws IOException {
                String[] ss = s.split(" ");

                if ( log.isTraceEnabled() ) {
                    log.trace("Creating command from: " + Arrays.toString(ss));
                }
                if ( 0 == col.compare(ss[ 0 ], "OK") ) {
                    this.command = COMMAND_OK;
                    this.data = ss[ 1 ];
                }
                else if ( 0 == col.compare(ss[ 0 ], "AUTH") ) {
                    this.command = COMMAND_AUTH;
                    if ( ss.length > 1 ) {
                        if ( 0 == col.compare(ss[ 1 ], "EXTERNAL") )
                            this.mechs = AUTH_EXTERNAL;
                        else if ( 0 == col.compare(ss[ 1 ], "DBUS_COOKIE_SHA1") )
                            this.mechs = AUTH_SHA;
                        else if ( 0 == col.compare(ss[ 1 ], "ANONYMOUS") )
                            this.mechs = AUTH_ANON;
                    }
                    if ( ss.length > 2 )
                        this.data = ss[ 2 ];
                }
                else if ( 0 == col.compare(ss[ 0 ], "DATA") ) {
                    this.command = COMMAND_DATA;
                    this.data = ss[ 1 ];
                }
                else if ( 0 == col.compare(ss[ 0 ], "REJECTED") ) {
                    this.command = COMMAND_REJECTED;
                    for ( int i = 1; i < ss.length; i++ )
                        if ( 0 == col.compare(ss[ i ], "EXTERNAL") )
                            this.mechs |= AUTH_EXTERNAL;
                        else if ( 0 == col.compare(ss[ i ], "DBUS_COOKIE_SHA1") )
                            this.mechs |= AUTH_SHA;
                        else if ( 0 == col.compare(ss[ i ], "ANONYMOUS") )
                            this.mechs |= AUTH_ANON;
                }
                else if ( 0 == col.compare(ss[ 0 ], "BEGIN") ) {
                    this.command = COMMAND_BEGIN;
                }
                else if ( 0 == col.compare(ss[ 0 ], "CANCEL") ) {
                    this.command = COMMAND_CANCEL;
                }
                else if ( 0 == col.compare(ss[ 0 ], "ERROR") ) {
                    this.command = COMMAND_ERROR;
                    this.data = ss[ 1 ];
                }
                else {
                    throw new IOException("Invalid Command " + ss[ 0 ]);
                }
                if ( log.isTraceEnabled() ) {
                    log.trace("Created command: " + this);
                }
            }


            public int getCommand () {
                return this.command;
            }


            public int getMechs () {
                return this.mechs;
            }


            public String getData () {
                return this.data;
            }


            public String getResponse () {
                return this.response;
            }


            public void setResponse ( String s ) {
                this.response = s;
            }


            @Override
            public String toString () {
                return "Command(" + this.command + ", " + this.mechs + ", " + this.data + ", " + null + ")";
            }
        }

        static Collator col = Collator.getInstance();
        static {
            col.setDecomposition(Collator.FULL_DECOMPOSITION);
            col.setStrength(Collator.PRIMARY);
        }
        public static final int LOCK_TIMEOUT = 1000;
        public static final int NEW_KEY_TIMEOUT_SECONDS = 60 * 5;
        public static final int EXPIRE_KEYS_TIMEOUT_SECONDS = NEW_KEY_TIMEOUT_SECONDS + ( 60 * 2 );
        public static final int MAX_TIME_TRAVEL_SECONDS = 60 * 5;
        public static final int COOKIE_TIMEOUT = 240;
        public static final String COOKIE_CONTEXT = "org_freedesktop_java";


        private static String findCookie ( String context, String ID ) throws IOException {
            String homedir = System.getProperty("user.home");
            File f = new File(homedir + "/.dbus-keyrings/" + context);
            try ( BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f))) ) {
                String s = null;
                String c = null;
                long now = System.currentTimeMillis() / 1000;
                while ( null != ( s = r.readLine() ) ) {
                    String[] line = s.split(" ");
                    long timestamp = Long.parseLong(line[ 1 ]);
                    if ( line[ 0 ].equals(ID)
                            && ( ! ( timestamp < 0 || ( now + MAX_TIME_TRAVEL_SECONDS ) < timestamp || ( now - EXPIRE_KEYS_TIMEOUT_SECONDS ) > timestamp ) ) ) {
                        c = line[ 2 ];
                        break;
                    }
                }
                return c;
            }

        }


        private static void addCookie ( String context, String ID, long timestamp, String c ) throws IOException {
            String homedir = System.getProperty("user.home");
            File keydir = new File(homedir + "/.dbus-keyrings/");
            File cookiefile = new File(homedir + "/.dbus-keyrings/" + context);
            File lock = new File(homedir + "/.dbus-keyrings/" + context + ".lock");
            File temp = new File(homedir + "/.dbus-keyrings/" + context + ".temp");

            // ensure directory exists
            if ( !keydir.exists() )
                keydir.mkdirs();

            // acquire lock
            long start = System.currentTimeMillis();
            while ( !lock.createNewFile() && LOCK_TIMEOUT > ( System.currentTimeMillis() - start ) );

            // read old file
            Vector<String> lines = new Vector<>();
            if ( cookiefile.exists() ) {
                try ( BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(cookiefile))) ) {
                    String s = null;
                    while ( null != ( s = r.readLine() ) ) {
                        String[] line = s.split(" ");
                        long time = Long.parseLong(line[ 1 ]);
                        // expire stale cookies
                        if ( ( timestamp - time ) < COOKIE_TIMEOUT )
                            lines.add(s);
                    }
                }
            }

            // add cookie
            lines.add(ID + " " + timestamp + " " + c);

            // write temp file
            try ( PrintWriter w = new PrintWriter(new FileOutputStream(temp)) ) {
                for ( String l : lines )
                    w.println(l);
            }

            // atomically move to old file
            if ( !temp.renameTo(cookiefile) ) {
                cookiefile.delete();
                temp.renameTo(cookiefile);
            }

            // remove lock
            lock.delete();
        }


        /**
         * Takes the string, encodes it as hex and then turns it into a string again.
         * No, I don't know why either.
         */
        private static String stupidlyEncode ( String data ) {
            return Hex.encodeHexString(data.getBytes());
        }


        private static String stupidlyEncode ( byte[] data ) {
            return Hex.encodeHexString(data);
        }


        private static byte getNibble ( char c ) {
            switch ( c ) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return (byte) ( c - '0' );
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
                return (byte) ( c - 'A' + 10 );
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
                return (byte) ( c - 'a' + 10 );
            default:
                return 0;
            }
        }


        private static String stupidlyDecode ( String data ) {
            char[] cs = new char[data.length()];
            char[] res = new char[cs.length / 2];
            data.getChars(0, data.length(), cs, 0);
            for ( int i = 0, j = 0; j < res.length; i += 2, j++ ) {
                int b = 0;
                b |= getNibble(cs[ i ]) << 4;
                b |= getNibble(cs[ i + 1 ]);
                res[ j ] = (char) b;
            }
            return new String(res);
        }

        public static final int MODE_SERVER = 1;
        public static final int MODE_CLIENT = 2;

        public static final int AUTH_NONE = 0;
        public static final int AUTH_EXTERNAL = 1;
        public static final int AUTH_SHA = 2;
        public static final int AUTH_ANON = 4;

        public static final int COMMAND_AUTH = 1;
        public static final int COMMAND_DATA = 2;
        public static final int COMMAND_REJECTED = 3;
        public static final int COMMAND_OK = 4;
        public static final int COMMAND_BEGIN = 5;
        public static final int COMMAND_CANCEL = 6;
        public static final int COMMAND_ERROR = 7;

        public static final int INITIAL_STATE = 0;
        public static final int WAIT_DATA = 1;
        public static final int WAIT_OK = 2;
        public static final int WAIT_REJECT = 3;
        public static final int WAIT_AUTH = 4;
        public static final int WAIT_BEGIN = 5;
        public static final int AUTHENTICATED = 6;
        public static final int FAILED = 7;

        public static final int OK = 1;
        public static final int CONTINUE = 2;
        public static final int ERROR = 3;
        public static final int REJECT = 4;


        public Command receive ( InputStream s ) throws IOException {
            StringBuffer sb = new StringBuffer();
            top:
            while ( true ) {
                int c = s.read();
                switch ( c ) {
                case -1:
                    throw new IOException("Stream unexpectedly short (broken pipe)");
                case 0:
                case '\r':
                    continue;
                case '\n':
                    break top;
                default:
                    sb.append((char) c);
                }
            }
            if ( log.isTraceEnabled() ) {
                log.trace("received: " + sb);
            }
            try {
                return new Command(sb.toString());
            }
            catch ( Exception e ) {
                log.warn(e);
                return new Command();
            }
        }


        public void send ( OutputStream out, int command, String... data ) throws IOException {
            StringBuffer sb = new StringBuffer();
            switch ( command ) {
            case COMMAND_AUTH:
                sb.append("AUTH");
                break;
            case COMMAND_DATA:
                sb.append("DATA");
                break;
            case COMMAND_REJECTED:
                sb.append("REJECTED");
                break;
            case COMMAND_OK:
                sb.append("OK");
                break;
            case COMMAND_BEGIN:
                sb.append("BEGIN");
                break;
            case COMMAND_CANCEL:
                sb.append("CANCEL");
                break;
            case COMMAND_ERROR:
                sb.append("ERROR");
                break;
            default:
                return;
            }
            for ( String s : data ) {
                sb.append(' ');
                sb.append(s);
            }
            sb.append('\r');
            sb.append('\n');
            if ( log.isTraceEnabled() ) {
                log.trace("sending: " + sb);
            }
            out.write(sb.toString().getBytes());
        }


        public int do_challenge ( int auth, Command c ) throws IOException {
            switch ( auth ) {
            case AUTH_SHA:
                String[] reply = stupidlyDecode(c.getData()).split(" ");

                if ( log.isTraceEnabled() ) {
                    log.trace(Arrays.toString(reply));
                }
                if ( 3 != reply.length ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Reply is not length 3");
                    }
                    return ERROR;
                }
                String context = reply[ 0 ];
                String ID = reply[ 1 ];
                String serverchallenge = reply[ 2 ];
                MessageDigest md = null;
                try {
                    md = MessageDigest.getInstance("SHA");
                }
                catch ( NoSuchAlgorithmException NSAe ) {
                    log.warn(NSAe);
                    return ERROR;
                }
                byte[] buf = new byte[8];
                Message.marshallintBig(System.currentTimeMillis(), buf, 0, 8);
                String clientchallenge = stupidlyEncode(md.digest(buf));
                md.reset();
                long start = System.currentTimeMillis();
                String challengeCookie = null;
                while ( null == challengeCookie && ( System.currentTimeMillis() - start ) < LOCK_TIMEOUT )
                    challengeCookie = findCookie(context, ID);
                if ( null == challengeCookie ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Did not find a cookie in context " + context + " with ID " + ID);
                    }
                    return ERROR;
                }
                String response = serverchallenge + ":" + clientchallenge + ":" + challengeCookie;
                buf = md.digest(response.getBytes());
                if ( log.isTraceEnabled() ) {
                    log.trace("Response: " + response + " hash: " + Hex.encodeHexString(buf));
                }
                response = stupidlyEncode(buf);
                c.setResponse(stupidlyEncode(clientchallenge + " " + response));
                return OK;
            default:
                if ( log.isDebugEnabled() ) {
                    log.debug("Not DBUS_COOKIE_SHA1 authtype.");
                }
                return ERROR;
            }
        }

        public String challenge = "";
        public String cookie = "";


        public int do_response ( int auth, String Uid, String kernelUid, Command c ) {
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance("SHA");
            }
            catch ( NoSuchAlgorithmException NSAe ) {
                log.warn(NSAe);
                return ERROR;
            }
            switch ( auth ) {
            case AUTH_NONE:
                switch ( c.getMechs() ) {
                case AUTH_ANON:
                    return OK;
                case AUTH_EXTERNAL:
                    if ( 0 == col.compare(Uid, c.getData()) && ( null == kernelUid || 0 == col.compare(Uid, kernelUid) ) )
                        return OK;

                    return ERROR;
                case AUTH_SHA:
                    String context = COOKIE_CONTEXT;
                    long id = System.currentTimeMillis();
                    byte[] buf = new byte[8];
                    Message.marshallintBig(id, buf, 0, 8);
                    this.challenge = stupidlyEncode(md.digest(buf));
                    Random r = new Random();
                    r.nextBytes(buf);
                    this.cookie = stupidlyEncode(md.digest(buf));
                    try {
                        addCookie(context, "" + id, id / 1000, this.cookie);
                    }
                    catch ( IOException IOe ) {
                        log.warn(IOe);
                    }
                    if ( log.isDebugEnabled() ) {
                        log.debug("Sending challenge: " + context + ' ' + id + ' ' + this.challenge);
                    }
                    c.setResponse(stupidlyEncode(context + ' ' + id + ' ' + this.challenge));
                    return CONTINUE;
                default:
                    return ERROR;
                }
            case AUTH_SHA:
                String[] response = stupidlyDecode(c.getData()).split(" ");
                if ( response.length < 2 )
                    return ERROR;
                String cchal = response[ 0 ];
                String hash = response[ 1 ];
                String prehash = this.challenge + ":" + cchal + ":" + this.cookie;
                byte[] buf = md.digest(prehash.getBytes());
                String posthash = stupidlyEncode(buf);
                if ( log.isDebugEnabled() ) {
                    log.debug("Authenticating Hash; data=" + prehash + " remote hash=" + hash + " local hash=" + posthash);
                }
                if ( 0 == col.compare(posthash, hash) )
                    return OK;

                return ERROR;
            default:
                return ERROR;
            }
        }


        public String[] getTypes ( int types ) {
            switch ( types ) {
            case AUTH_EXTERNAL:
                return new String[] {
                    "EXTERNAL"
                };
            case AUTH_SHA:
                return new String[] {
                    "DBUS_COOKIE_SHA1"
                };
            case AUTH_ANON:
                return new String[] {
                    "ANONYMOUS"
                };
            case AUTH_SHA + AUTH_EXTERNAL:
                return new String[] {
                    "EXTERNAL", "DBUS_COOKIE_SHA1"
                };
            case AUTH_SHA + AUTH_ANON:
                return new String[] {
                    "ANONYMOUS", "DBUS_COOKIE_SHA1"
                };
            case AUTH_EXTERNAL + AUTH_ANON:
                return new String[] {
                    "ANONYMOUS", "EXTERNAL"
                };
            case AUTH_EXTERNAL + AUTH_ANON + AUTH_SHA:
                return new String[] {
                    "ANONYMOUS", "EXTERNAL", "DBUS_COOKIE_SHA1"
                };
            default:
                return new String[] {};
            }
        }


        /**
         * performs SASL auth on the given streams.
         * Mode selects whether to run as a SASL server or client.
         * Types is a bitmask of the available auth types.
         * Returns true if the auth was successful and false if it failed.
         */
        public boolean auth ( int mode, int types, String guid, OutputStream out, InputStream in, AFUNIXSocket us ) throws IOException {
            String username = System.getProperty("user.name");
            String Uid = null;
            String kernelUid = null;
            try {
                Class<?> c = Class.forName("com.sun.security.auth.module.UnixSystem");
                Method m = c.getMethod("getUid");
                Object o = c.newInstance();
                long uid = (Long) m.invoke(o);
                log.debug("Got UID " + uid);
                Uid = "" + uid;
            }
            catch ( Exception e ) {
                Uid = username;
            }
            Uid = Hex.encodeHexString(Uid.getBytes("ASCII"));
            Command c;
            int failed = 0;
            int current = 0;
            int state = INITIAL_STATE;

            while ( state != AUTHENTICATED && state != FAILED ) {
                if ( log.isTraceEnabled() ) {
                    log.trace("AUTH state: " + state);
                }
                switch ( mode ) {
                case MODE_CLIENT:
                    switch ( state ) {
                    case INITIAL_STATE:
                        if ( null == us )
                            out.write(new byte[] {
                                0
                            });
                        else {
                            log.debug("Sending credentials");
                            us.sendCredentials((byte) 0);
                        }
                        send(out, COMMAND_AUTH);
                        state = WAIT_DATA;
                        break;
                    case WAIT_DATA:
                        c = receive(in);
                        switch ( c.getCommand() ) {
                        case COMMAND_DATA:
                            switch ( do_challenge(current, c) ) {
                            case CONTINUE:
                                send(out, COMMAND_DATA, c.getResponse());
                                break;
                            case OK:
                                send(out, COMMAND_DATA, c.getResponse());
                                state = WAIT_OK;
                                break;
                            case ERROR:
                                send(out, COMMAND_ERROR, c.getResponse());
                                break;
                            }
                            break;
                        case COMMAND_REJECTED:
                            failed |= current;
                            int available = c.getMechs() & ( ~failed );
                            if ( 0 != ( available & AUTH_EXTERNAL ) ) {
                                log.debug("Trying EXTERNAL with " + Uid);
                                send(out, COMMAND_AUTH, "EXTERNAL", Uid);
                                current = AUTH_EXTERNAL;
                            }
                            else if ( 0 != ( available & AUTH_SHA ) ) {
                                log.debug("Trying DBUS_COOKIE_SHA1 with " + Uid);
                                send(out, COMMAND_AUTH, "DBUS_COOKIE_SHA1", Uid);
                                current = AUTH_SHA;
                            }
                            else if ( 0 != ( available & AUTH_ANON ) ) {
                                log.debug("Trying ANONYMOUS");
                                send(out, COMMAND_AUTH, "ANONYMOUS");
                                current = AUTH_ANON;
                            }
                            else
                                state = FAILED;
                            break;
                        case COMMAND_ERROR:
                            send(out, COMMAND_CANCEL);
                            state = WAIT_REJECT;
                            break;
                        case COMMAND_OK:
                            send(out, COMMAND_BEGIN);
                            state = AUTHENTICATED;
                            break;
                        default:
                            send(out, COMMAND_ERROR, "Got invalid command");
                            break;
                        }
                        break;
                    case WAIT_OK:
                        c = receive(in);
                        switch ( c.getCommand() ) {
                        case COMMAND_OK:
                            send(out, COMMAND_BEGIN);
                            state = AUTHENTICATED;
                            break;
                        case COMMAND_ERROR:
                        case COMMAND_DATA:
                            send(out, COMMAND_CANCEL);
                            state = WAIT_REJECT;
                            break;
                        case COMMAND_REJECTED:
                            failed |= current;
                            int available = c.getMechs() & ( ~failed );
                            state = WAIT_DATA;
                            if ( 0 != ( available & AUTH_EXTERNAL ) ) {
                                send(out, COMMAND_AUTH, "EXTERNAL", Uid);
                                current = AUTH_EXTERNAL;
                            }
                            else if ( 0 != ( available & AUTH_SHA ) ) {
                                send(out, COMMAND_AUTH, "DBUS_COOKIE_SHA1", Uid);
                                current = AUTH_SHA;
                            }
                            else if ( 0 != ( available & AUTH_ANON ) ) {
                                send(out, COMMAND_AUTH, "ANONYMOUS");
                                current = AUTH_ANON;
                            }
                            else
                                state = FAILED;
                            break;
                        default:
                            send(out, COMMAND_ERROR, "Got invalid command");
                            break;
                        }
                        break;
                    case WAIT_REJECT:
                        c = receive(in);
                        switch ( c.getCommand() ) {
                        case COMMAND_REJECTED:
                            failed |= current;
                            int available = c.getMechs() & ( ~failed );
                            if ( 0 != ( available & AUTH_EXTERNAL ) ) {
                                send(out, COMMAND_AUTH, "EXTERNAL", Uid);
                                current = AUTH_EXTERNAL;
                            }
                            else if ( 0 != ( available & AUTH_SHA ) ) {
                                send(out, COMMAND_AUTH, "DBUS_COOKIE_SHA1", Uid);
                                current = AUTH_SHA;
                            }
                            else if ( 0 != ( available & AUTH_ANON ) ) {
                                send(out, COMMAND_AUTH, "ANONYMOUS");
                                current = AUTH_ANON;
                            }
                            else
                                state = FAILED;
                            break;
                        default:
                            state = FAILED;
                            break;
                        }
                        break;
                    default:
                        state = FAILED;
                    }
                    break;
                case MODE_SERVER:
                    switch ( state ) {
                    case INITIAL_STATE:
                        byte[] buf = new byte[1];
                        if ( null == us ) {
                            in.read(buf);
                        }
                        else {
                            SocketCredentials creds = us.recieveCredentials();
                            if ( creds.getUid() >= 0 )
                                kernelUid = stupidlyEncode("" + creds.getUid());
                        }
                        if ( 0 != buf[ 0 ] )
                            state = FAILED;
                        else
                            state = WAIT_AUTH;
                        break;
                    case WAIT_AUTH:
                        c = receive(in);
                        switch ( c.getCommand() ) {
                        case COMMAND_AUTH:
                            if ( null == c.getData() ) {
                                send(out, COMMAND_REJECTED, getTypes(types));
                            }
                            else {
                                switch ( do_response(current, Uid, kernelUid, c) ) {
                                case CONTINUE:
                                    send(out, COMMAND_DATA, c.getResponse());
                                    current = c.getMechs();
                                    state = WAIT_DATA;
                                    break;
                                case OK:
                                    send(out, COMMAND_OK, guid);
                                    state = WAIT_BEGIN;
                                    current = 0;
                                    break;
                                case REJECT:
                                    send(out, COMMAND_REJECTED, getTypes(types));
                                    current = 0;
                                    break;
                                }
                            }
                            break;
                        case COMMAND_ERROR:
                            send(out, COMMAND_REJECTED, getTypes(types));
                            break;
                        case COMMAND_BEGIN:
                            state = FAILED;
                            break;
                        default:
                            send(out, COMMAND_ERROR, "Got invalid command");
                            break;
                        }
                        break;
                    case WAIT_DATA:
                        c = receive(in);
                        switch ( c.getCommand() ) {
                        case COMMAND_DATA:
                            switch ( do_response(current, Uid, kernelUid, c) ) {
                            case CONTINUE:
                                send(out, COMMAND_DATA, c.getResponse());
                                state = WAIT_DATA;
                                break;
                            case OK:
                                send(out, COMMAND_OK, guid);
                                state = WAIT_BEGIN;
                                current = 0;
                                break;
                            case REJECT:
                                send(out, COMMAND_REJECTED, getTypes(types));
                                current = 0;
                                break;
                            }
                            break;
                        case COMMAND_ERROR:
                        case COMMAND_CANCEL:
                            send(out, COMMAND_REJECTED, getTypes(types));
                            state = WAIT_AUTH;
                            break;
                        case COMMAND_BEGIN:
                            state = FAILED;
                            break;
                        default:
                            send(out, COMMAND_ERROR, "Got invalid command");
                            break;
                        }
                        break;
                    case WAIT_BEGIN:
                        c = receive(in);
                        switch ( c.getCommand() ) {
                        case COMMAND_ERROR:
                        case COMMAND_CANCEL:
                            send(out, COMMAND_REJECTED, getTypes(types));
                            state = WAIT_AUTH;
                            break;
                        case COMMAND_BEGIN:
                            state = AUTHENTICATED;
                            break;
                        default:
                            send(out, COMMAND_ERROR, "Got invalid command");
                            break;
                        }
                        break;
                    default:
                        state = FAILED;
                    }
                    break;
                default:
                    return false;
                }
            }

            return state == AUTHENTICATED;
        }
    }

    public MessageReader min;
    public MessageWriter mout;


    public Transport () {}


    public static String genGUID () {
        Random r = new Random();
        byte[] buf = new byte[16];
        r.nextBytes(buf);
        return Hex.encodeHexString(buf);
    }


    public Transport ( BusAddress address ) throws IOException {
        connect(address);
    }


    public Transport ( String address ) throws IOException, ParseException {
        connect(new BusAddress(address));
    }


    public Transport ( String address, int timeout ) throws IOException, ParseException {
        connect(new BusAddress(address), timeout);
    }


    public void connect ( String address ) throws IOException, ParseException {
        connect(new BusAddress(address), 0);
    }


    public void connect ( String address, int timeout ) throws IOException, ParseException {
        connect(new BusAddress(address), timeout);
    }


    public void connect ( BusAddress address ) throws IOException {
        connect(address, 0);
    }


    @SuppressWarnings ( "resource" )
    public void connect ( BusAddress address, int timeout ) throws IOException {
        log.info("Connecting to " + address);
        OutputStream out = null;
        InputStream in = null;
        AFUNIXSocket us = null;
        Socket s = null;
        int mode = 0;
        int types = 0;
        if ( "unix".equals(address.getType()) ) {
            types = SASL.AUTH_EXTERNAL;
            File sockFile = null;
            boolean abstractSock = false;
            if ( null != address.getParameter("abstract") ) {
                sockFile = new File(address.getParameter("abstract"));
                abstractSock = true;
            }
            else if ( null != address.getParameter("path") ) {
                sockFile = new File(address.getParameter("path"));
            }

            if ( null != address.getParameter("listen") ) {
                mode = SASL.MODE_SERVER;
                AFUNIXServerSocket uss = AFUNIXServerSocket.newInstance();
                uss.bind(new AFUNIXSocketAddress(sockFile, abstractSock));
                uss.setPassCred(true);
                s = uss.accept();
            }
            else {
                mode = SASL.MODE_CLIENT;
                us = AFUNIXSocket.newInstance();

                us.connect(new AFUNIXSocketAddress(sockFile, abstractSock));
                us.setPassCred(true);
                s = us;
            }

        }
        else if ( "tcp".equals(address.getType()) ) {
            types = SASL.AUTH_SHA;
            if ( null != address.getParameter("listen") ) {
                mode = SASL.MODE_SERVER;
                ServerSocket ss = new ServerSocket();
                ss.bind(new InetSocketAddress(address.getParameter("host"), Integer.parseInt(address.getParameter("port"))));
                s = ss.accept();
            }
            else {
                mode = SASL.MODE_CLIENT;
                s = new Socket();
                s.connect(new InetSocketAddress(address.getParameter("host"), Integer.parseInt(address.getParameter("port"))));
            }
        }
        else {
            throw new IOException("unknown address type " + address.getType());
        }

        in = s.getInputStream();
        out = s.getOutputStream();

        if ( ! ( new SASL() ).auth(mode, types, address.getParameter("guid"), out, in, us) ) {
            out.close();
            in.close();
            s.close();
            throw new IOException("Failed to auth");
        }

        if ( log.isTraceEnabled() ) {
            log.trace("Setting timeout to " + timeout + " on Socket");
        }
        s.setSoTimeout(timeout);

        this.mout = new MessageWriter(out);
        this.min = new MessageReader(in);

        log.info("Connection open");
    }


    public void disconnect () throws IOException {
        log.info("Disconnecting Transport");
        this.min.close();
        this.mout.close();
    }
}
