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

package com.github.tracinstant.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import com.github.tracinstant.app.data.SavedSearch;

final class SearchDropDownRenderer extends DefaultListCellRenderer {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    JPanel panel = new JPanel(new BorderLayout());
    Box box = Box.createHorizontalBox();
    JLabel desc = new JLabel(" ");
    JLabel alias = new JLabel(" ");
    JLabel searchText = new JLabel(" ");
    Font monoFont = new Font(Font.MONOSPACED, Font.BOLD, 12);
    Font descFont = desc.getFont().deriveFont(desc.getFont().getSize() + 2f);

    SearchDropDownRenderer() {
        desc.setBackground(TRANSPARENT);
        desc.setFont(descFont);
        desc.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        alias.setBackground(TRANSPARENT);
        alias.setFont(monoFont);

        searchText.setBackground(TRANSPARENT);
        searchText.setFont(monoFont);

        box.add(alias);
        box.add(searchText);
        box.add(Box.createGlue());
        box.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 6));

        panel.add(desc, BorderLayout.NORTH);
        panel.add(box, BorderLayout.SOUTH);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
            boolean isSelected, boolean cellHasFocus) {
        SavedSearch ss = (SavedSearch) value;

        Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();

        desc.setText(ss.name.isEmpty() ? "" : ss.name);
        desc.setForeground(fg);

        alias.setText(ss.alias);
        alias.setForeground(isSelected ? fg : Color.BLUE);

        String searchBase = ss.alias.isEmpty() ? "" : " : ";
        searchText.setText(searchBase + ss.searchText);
        searchText.setForeground(isSelected ? fg : Color.GRAY);

        Color bg = isSelected ? list.getSelectionBackground() : TRANSPARENT;
        panel.setBackground(bg);
        panel.setOpaque(isSelected);
        return panel;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}