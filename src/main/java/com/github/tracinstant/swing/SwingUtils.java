/*
 * Copyright 2011 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.tracinstant.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

public final class SwingUtils {
    private SwingUtils() {}

    public static Window getWindowForComponent(Component comp) {
        assert comp != null;
        for (; comp != null; comp = comp.getParent()) {
            if (comp instanceof Frame || comp instanceof Dialog) {

                return (Window) comp;
            }
        }
        throw new IllegalStateException();
    }

    /**
     * Note: in addition to calling this, it is recommended to also call:
     * 
     * <p>{@code text.setDocument(new CustomUndoPlainDocument());}
     */
    public static void addUndoSupport(JTextComponent text) {
        UndoManager undo = new UndoManager();
        text.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "UndoKeystroke");
        text.getInputMap().put(KeyStroke.getKeyStroke("control Y"), "RedoKeystroke");
        text.getActionMap().put("UndoKeystroke", new AbstractAction("Undo") {
            @Override
            public void actionPerformed(ActionEvent evt) {
                try {
                    undo.undo();
                } catch (CannotUndoException e) {
                }
            }
        });
        text.getActionMap().put("RedoKeystroke", new AbstractAction("Redo") {
            @Override
            public void actionPerformed(ActionEvent evt) {
                try {
                    undo.redo();
                } catch (CannotRedoException e) {
                }
            }
        });
        text.getDocument().addUndoableEditListener(undo);
    }
}
