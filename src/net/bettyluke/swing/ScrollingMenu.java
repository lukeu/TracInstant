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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicArrowButton;

// TODO: Implement correct keyboard cursor behaviour - scrolling past end of view and
// page-up/page-down
public final class ScrollingMenu extends JPopupMenu {

    private final BasicArrowButton upButton = createArrow(SwingConstants.NORTH);
    private final BasicArrowButton downButton = createArrow(SwingConstants.SOUTH);
    private final Controller controller = new Controller();
    private final Component[] menuItems;
    private final int viewLength;

    private int topComp = 0;

    /**
     * In lieu of a full MVC separation, this class at least separates out the
     * behavioural logic from view objects.
     */
    private final class Controller extends MouseAdapter implements ActionListener {

        private Timer timer = new Timer(66, this);
        private float increment = 1;

        public void attach() {
            upButton.addMouseListener(this);
            downButton.addMouseListener(this);
            ScrollingMenu.this.addMouseWheelListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            shiftTo(clampPosition(topComp + (int) increment));
            increment *= 1.1;
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            int scrollAmount = e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL ?
                viewLength : e.getUnitsToScroll();
            shiftTo(clampPosition(topComp + scrollAmount));
            e.consume();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            increment = (e.getSource() == upButton) ? -1 : 1;
            timer.start();
            ((AbstractButton) e.getSource()).getModel().setPressed(true);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            timer.stop();
            ((AbstractButton) e.getSource()).getModel().setPressed(false);
        }

        private int clampPosition(int newTop) {
            return Math.max(0, Math.min(newTop, end() - 1));
        }
    }

    /** Constructor which <b>rips</b> all the menu-items out of menu, taking them over. */
    public ScrollingMenu(JPopupMenu menu, int visibleLength) {
        this.menuItems = Arrays.copyOf(menu.getComponents(), menu.getComponentCount());
        menu.removeAll();
        this.viewLength = visibleLength - 2;
        upButton.setEnabled(false);
        add(upButton);
        for (int i = 0; i < viewLength; ++i) {
            add(menuItems[i]);
        }
        add(downButton);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        controller.attach();
    }

    /**
     * Shifts the menu to display items beginning at the given index, and disables the
     * up/down buttons as appropriate.
     */
    public void shiftTo(int newTop) {
        upButton.setEnabled(newTop > 0);
        downButton.setEnabled(newTop < end() - 1);

        if (newTop < topComp) {
            for (int i = topComp - 1; i >= newTop; --i) {
                assert menuItems[i].getParent() == null;
                remove(viewLength);
                insert(menuItems[i], 1);
            }
        } else {
            for (int i = topComp; i < newTop; ++i) {
                Component toAdd = menuItems[i + viewLength];
                assert toAdd.getParent() == null;
                insert(toAdd, viewLength + 1);
                remove(1);
            }
        }
        topComp = newTop;
        invalidate();
        validate();
        repaint();
    }

    public int end() {
        return menuItems.length - viewLength;
    }

    private static BasicArrowButton createArrow(int dir) {
        BasicArrowButton result = new BasicArrowButton(
                dir,
                new JPopupMenu().getBackground(),
                UIManager.getColor("controlShadow"),
                Color.DARK_GRAY,
                UIManager.getColor("controlLtHighlight")) {

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(24, 24);
            }
        };
        return result;
    }
}
