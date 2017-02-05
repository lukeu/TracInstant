/*
 * Copyright 2011 Luke Usherwood.
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

package net.bettyluke.swing;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

public class StatusWidget {

    public class HalfSizeIcon implements Icon {
        private Icon original;

        public HalfSizeIcon(Icon icon) {
            original = icon;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D gfx = (Graphics2D) g.create();
            gfx.scale(0.5, 0.5);
            gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                 RenderingHints.VALUE_ANTIALIAS_ON);
            gfx.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                 RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            original.paintIcon(c, gfx, x*2, y);
        }

        @Override
        public int getIconWidth() {
            return original.getIconWidth() / 2;
        }

        @Override
        public int getIconHeight() {
            return original.getIconWidth() / 2;
        }
    }

    public static final Icon BUSY_IMAGE = new ImageIcon(
            StatusWidget.class.getResource("res/animated-wait.gif"));

    private JLabel label = new JLabel();

    public StatusWidget() {
        label.setVisible(false);
        label.setHorizontalTextPosition(SwingConstants.LEFT);
    }

    public JComponent getComponent() {
        return label;
    }

    public void hide() {
        label.setToolTipText(null);
        label.setIcon(null);
        label.setVisible(false);
    }

    public void showBusy(String labelText, String toolTipText) {
        label.setText(labelText);
        label.setToolTipText(toolTipText);
        label.setIcon(BUSY_IMAGE);
        label.setVisible(true);
    }

    public void showError(String labelText, String toolTipText) {
        label.setText(labelText);
        label.setToolTipText(toolTipText);
        label.setIcon(new HalfSizeIcon(UIManager.getIcon("OptionPane.errorIcon")));
        label.setVisible(true);
    }

    public void showWarning(String labelText, String toolTipText) {
        label.setText(labelText);
        label.setToolTipText(toolTipText);
        label.setIcon(new HalfSizeIcon(UIManager.getIcon("OptionPane.warningIcon")));
        label.setVisible(true);
    }
}
