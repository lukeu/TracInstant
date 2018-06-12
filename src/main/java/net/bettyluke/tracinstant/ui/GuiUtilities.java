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

package net.bettyluke.tracinstant.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingConstants;

public final class GuiUtilities {
    private GuiUtilities() {}

    public static JButton createHyperlinkButton(String text, String tip, ActionListener action) {
        JButton button = new JButton();
        button.setText("<html><a href=\"#\">" + text + "</a></html>");
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBackground(new Color(0, 0, 0, 0));
        button.setToolTipText(tip);
        button.addActionListener(action);
        return button;
    }

    /**
     * Makes the maximum size a smidge wider than the current preferred size.
     * NB: Side-effect - this currently also sets an unlimited height.
     */
    public static void makeMaxASmidgeWider(JComponent comp, int smidge) {
        comp.setPreferredSize(null);
        Dimension dims = comp.getPreferredSize();
        dims.width += smidge;
        comp.setPreferredSize(dims);
        comp.setMaximumSize(new Dimension(dims.width, Integer.MAX_VALUE));
    }
}
