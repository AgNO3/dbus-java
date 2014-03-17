/**
 * © 2014 AgNO3 Gmbh & Co. KG
 * All right reserved.
 * 
 * Created: 10.03.2014 by mbechler
 */
package org.freedesktop.dbus.types;


/**
 * @author mbechler
 * 
 */
public class UnixFD extends UInt32 {

    /**
     * @param value
     */
    public UnixFD ( long value ) {
        super(value);
    }

    /**
     * 
     */
    private static final long serialVersionUID = 6687686369749692538L;

}
