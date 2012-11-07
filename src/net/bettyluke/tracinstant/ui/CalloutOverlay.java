/*
 * Copyright 2011 Luke Usherwood.
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
        
package net.bettyluke.tracinstant.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.InternalFrameUI;
import javax.swing.plaf.basic.BasicInternalFrameUI;
import javax.swing.text.JTextComponent;

public class CalloutOverlay {
    
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 30);
    
    public static class TrianglePanel extends JPanel {
        
        final int[] xPoints;
        final int[] yPoints;
        
        public TrianglePanel(int size) {
            setOpaque(false);
            xPoints = new int[] { size, size * 2, 0 };
            yPoints = new int[] { 0, size, size };
            setPreferredSize(new Dimension(size*2, size));
        }
        
        @Override
        public void paintComponent(Graphics g) {
            Color oldColor = g.getColor();
            g.setColor(getBackground());
            g.fillPolygon(xPoints, yPoints, xPoints.length);
            g.setColor(getForeground());
            g.drawLine(xPoints[0], yPoints[0], xPoints[1], yPoints[1]);
            g.drawLine(xPoints[0], yPoints[0], xPoints[2], yPoints[2]);
            g.setColor(oldColor);
        }

        public void locatePointAt(int x, int y) {
            int size = getPreferredSize().height;
            setBounds(x - size, y, size*2, size);
        }
    }
    
    /**
     * A poor-man's drop-shadow. TODO: Add blur.
     */
    public class ShadowPanel extends JPanel {
        public ShadowPanel(JComponent comp) {
            comp.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentMoved(ComponentEvent e) {
                    setNewBounds(e.getComponent().getBounds());
                }
                @Override
                public void componentResized(ComponentEvent e) {
                    setNewBounds(e.getComponent().getBounds());
                }
            });

            setOpaque(true);
            setBackground(SHADOW_COLOR);
            setNewBounds(comp.getBounds());
        }

        private void setNewBounds(Rectangle bounds) {
            bounds.translate(3, 3);
            setBounds(bounds);
        }
    }

    public static class UntitledFrame extends JInternalFrame {
        public UntitledFrame(JComponent content) {
            super("");
            add(content);

            // Hack away the title-bar
            InternalFrameUI ifu = getUI();
            if (ifu instanceof BasicInternalFrameUI) {
                ((BasicInternalFrameUI) ifu).setNorthPane(null);
            }
        }
    }

    private final class MyMouseListener implements MouseListener, MouseMotionListener {

        public void mouseClicked(MouseEvent e) {
            redispatchToCallout(e);
        }
        public void mousePressed(MouseEvent e) {
            if (!redispatchToCallout(e)) {
                if (dismissListener != null) {
                    ActionEvent evt = new ActionEvent(this, 0, "Dismiss callout");
                    dismissListener.actionPerformed(evt);
                }
                e.consume();
            }
        }
        public void mouseReleased(MouseEvent e) {
            redispatchToCallout(e);
        }
        public void mouseEntered(MouseEvent e) {
            redispatchToCallout(e);
        }
        public void mouseExited(MouseEvent e) {
            redispatchToCallout(e);
        }
        public void mouseDragged(MouseEvent e) {
            redispatchToCallout(e);
        }
        public void mouseMoved(MouseEvent e) {
            redispatchToCallout(e);
        }

        private boolean redispatchToCallout(MouseEvent e) {
            if (iFrame.getBounds().contains(e.getPoint())) {
                redispatchMouseEvent(e);
                return true;
            }
            return false;
        }
        
        private void redispatchMouseEvent(MouseEvent e) {
            Point glassPanePoint = e.getPoint();
            Point layeredPanePoint = SwingUtilities.convertPoint(glassPane,
                glassPanePoint, layeredPane);

            if (layeredPanePoint.y >= 0) {
                // The mouse event is probably over the content pane.
                // Find out exactly which component it's over.
                Component component = SwingUtilities.getDeepestComponentAt(
                    layeredPane, layeredPanePoint.x, layeredPanePoint.y);

                if (component != null)  {
                    Point componentPoint = SwingUtilities.convertPoint(
                        glassPane, glassPanePoint, component);
                    component.dispatchEvent(new MouseEvent(component, e
                        .getID(), e.getWhen(), e.getModifiers(),
                        componentPoint.x, componentPoint.y,
                        e.getClickCount(), e.isPopupTrigger()));
                    
                }
                
                // HACK! Since this code can't correctly generate mouse-entered and
                // mouse-exit events for Components placed in the glass pane, we
                // take over the job of changing to a Text cursor, very crudely.
                glassPane.setCursor((component instanceof JTextComponent) ?
                    Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) : null);
            }
        }
    }
    
    private final JLayeredPane layeredPane;

    private final TrianglePanel triangle;
    private final JInternalFrame iFrame;
    private final JPanel shad;
    private final Component glassPane;
    private final MyMouseListener mouseListener = new MyMouseListener();
    
    private ActionListener dismissListener;
    
    public CalloutOverlay(JFrame frame, JComponent content) {
        iFrame = new UntitledFrame(content);
        shad = new ShadowPanel(iFrame);
        layeredPane = frame.getLayeredPane();
        glassPane = frame.getGlassPane();
        triangle = new TrianglePanel(15);
        triangle.setBackground(content.getBackground());
    }

    public void showAt(int x, int y) {
        triangle.locatePointAt(x, y);
        iFrame.pack();
        iFrame.setLocation(
            x - iFrame.getWidth() + triangle.getWidth(),
            y + triangle.getHeight() - 1);
        
        layeredPane.add(triangle, Integer.valueOf(JLayeredPane.POPUP_LAYER + 1));
        layeredPane.add(iFrame, Integer.valueOf(JLayeredPane.POPUP_LAYER));
        layeredPane.add(shad, Integer.valueOf(JLayeredPane.POPUP_LAYER - 1));
        glassPane.setVisible(true);
        glassPane.addMouseListener(mouseListener);
        glassPane.addMouseMotionListener(mouseListener);
        iFrame.show();
    }
    
    public Component getContent() {
        return iFrame.getContentPane().getComponent(0);
    }
    
    public void dismiss() {
        layeredPane.remove(triangle);
        layeredPane.remove(iFrame);
        layeredPane.remove(shad);
        glassPane.removeMouseListener(mouseListener);
        glassPane.removeMouseMotionListener(mouseListener);
        glassPane.setVisible(false);
        iFrame.dispose();
        
        // TODO: is there a way to ensure the cursor updates appropriately
        // to the component under the glass, before the mouse moves?
    }

    public void addDismissListener(ActionListener listener) {
        if (dismissListener != null) {
            throw new IllegalStateException();
        }
        dismissListener = listener;
    }
}
