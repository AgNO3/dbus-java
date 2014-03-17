/*
   D-Bus Java Viewer
   Copyright (c) 2006 Peter Cox

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus.bin.viewer;


import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.swing.Action;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


@SuppressWarnings ( "serial" )
class SaveFileAction extends TabbedSaveAction implements ChangeListener {

    private class SelectedTabIterator implements Iterator<TextFile> {

        boolean iterated = false;


        /**
         * 
         */
        public SelectedTabIterator () {}


        /** {@inheritDoc} */
        @Override
        public boolean hasNext () {
            return !this.iterated;
        }


        /** {@inheritDoc} */
        @Override
        public TextFile next () {
            if ( this.iterated ) {
                throw new NoSuchElementException("Already iterated");
            }
            this.iterated = true;
            return getTextFile(SaveFileAction.this.tabbedPane.getSelectedIndex());
        }


        /** {@inheritDoc} */
        @Override
        public void remove () {
            throw new UnsupportedOperationException();
        }

    }


    SaveFileAction ( JTabbedPane tabbedPane ) {
        super(tabbedPane);

        enableAndSetName();

        tabbedPane.addChangeListener(this);
    }


    /** {@inheritDoc} */
    @Override
    public void stateChanged ( ChangeEvent e ) {
        enableAndSetName();
    }


    /**
     * Enable and set the name of the action based on the shown tab
     */
    void enableAndSetName () {
        int selectedIndex = this.tabbedPane.getSelectedIndex();
        boolean en = selectedIndex > -1;
        putValue(Action.NAME, "Save " + getFileName(selectedIndex) + "...");
        setEnabled(en);
    }


    /** {@inheritDoc} */
    @Override
    public Iterator<TextFile> iterator () {
        return new SelectedTabIterator();
    }
}
