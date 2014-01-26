/*
 * Copyright 2014 Luke Usherwood.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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

package net.bettyluke.util.swing.monitor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

class PercentageTimeTableRenderer extends JLabel implements TableCellRenderer {

    private static int DULL = 150;
    private static int BRIGHT = 220;
    private static final Color SHORT_COLOR = new Color(0, DULL, 0);
    private static final Color MEDIUM_COLOR  = new Color(DULL, DULL, 0);
    private static final Color LONG_COLOR = new Color(DULL, 0, 0);
    private static final Color SHORT_SELECTED_COLOR  = new Color(0, BRIGHT, 0);
    private static final Color MEDIUM_SELECTED_COLOR  = new Color(BRIGHT, BRIGHT, 0);
    private static final Color LONG_SELECTED_COLOR = new Color(BRIGHT, 0, 0);

    private PeriodStatistics bin;
    private boolean highlight;

    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        bin = (PeriodStatistics) value;
        highlight = isSelected;
        setBackground(table.getBackground());
        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(true);
        setText(null);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g = g.create();
        int h = getHeight();
        int y = h;
        if (bin == null) {
            return;
        }
        if (bin.getLong().count > 0) {
            g.setColor(highlight ? LONG_SELECTED_COLOR : LONG_COLOR);
            y = fill(g, y, bin.getLong().nanos);
        }
        if (bin.getMedium().count > 0) {
            g.setColor(highlight ? MEDIUM_SELECTED_COLOR : MEDIUM_COLOR );
            y = fill(g, y, bin.getMedium().nanos);
        }
        if (bin.getShort().count > 0) {
            g.setColor(highlight ? SHORT_SELECTED_COLOR : SHORT_COLOR );
            y = fill(g, y, bin.getShort().nanos);
        }
        g.setColor(getBackground());
        y = h - (int) (bin.longestEvent * h / bin.reportingIntervalNanos) - 1;
        g.drawLine(0, y, getWidth(), y);
    }

    private int fill(Graphics g, int y, long nanos) {
        int y2 = y - (int) (nanos * y / bin.reportingIntervalNanos);
        if (y2 < 1) y2 = 1;
        g.fillRect(0, y2, getWidth(), y-y2);
        return y2;
    }
}
