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

package net.bettyluke.util;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class DocUtils {
    private DocUtils() {}

    // A new listener is created to help the user think about whether it needs to be removed again
    // (to avoid memory leaks) even though this may not always be necessary.
    public static DocumentListener newOnAnyEventListener(Runnable runner) {
        return new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                runner.run();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                runner.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                runner.run();
            }
        };
    }
}
