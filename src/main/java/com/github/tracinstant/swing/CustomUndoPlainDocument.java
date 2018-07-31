/*-***************************************************************************

  ===========================================================================
            Copyright (c) 2018-2018 Process Systems Enterprise Ltd.
  ===========================================================================

                                 LEGAL NOTICE
                                 ------------
    These coded instructions, statements, and computer programs contain
    proprietary information belonging to Process Systems Enterprise Ltd.,
    and are protected by International copyright law. They may not be
    disclosed to third parties without the prior written consent of
    Process Systems Enterprise Ltd.

*****************************************************************************/

package com.github.tracinstant.swing;

import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.undo.CompoundEdit;

/**
 * Modifies "replace" operations (such as pasting, selecting a new combo-box item) such that they
 * are undone in one step rather than 2 separate [remove, insert] steps.
 *
 * (i.e. "atomically" but I didn't want to call the class that due to threading connotations.)
 */
public final class CustomUndoPlainDocument extends PlainDocument {
    @Override
    public void replace(int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        if (length == 0) {
            // insert
            super.replace(offset, length, text, attrs);
        } else {
            CompoundEdit compoundEdit = new CompoundEdit();
            super.fireUndoableEditUpdate(new UndoableEditEvent(this, compoundEdit));
            super.replace(offset, length, text, attrs);
            compoundEdit.end();
        }
    }
}
