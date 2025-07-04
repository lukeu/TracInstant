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

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.plaf.TextUI;
import javax.swing.plaf.synth.SynthTextFieldUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.FieldView;
import javax.swing.text.Segment;
import javax.swing.text.Utilities;
import javax.swing.text.View;

import com.github.tracinstant.app.data.SavedSearch;
import com.github.tracinstant.swing.CustomUndoPlainDocument;
import com.github.tracinstant.swing.SwingUtils;
import com.github.tracinstant.util.DocUtils;

public class SearchComboEditor extends JTextField {

    private final static class GradientBox extends Box {
        GradientBox() {
            super(BoxLayout.Y_AXIS);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            Shape clip = g2.getClip();
            int h = getHeight();
            Color bg = getBackground();
            g2.setPaint(new GradientPaint(0, 0, bg, 0, h, bg.darker()));
            g2.fill(clip);
        }
    }

    private class SavedSearchCallout extends CalloutOverlay {

        private final JTextField shorthand = new JTextField();
        private final JTextField desc = new JTextField();

        public SavedSearchCallout(JFrame wind) {
            super(wind, new GradientBox());

            final Action doneAction = createDoneAction();
            final Action cancelAction = createCancelAction();
            final Action removeSearchAction = createRemoveSearchAction();

            JButton removeStarButton = new JButton(removeSearchAction);
            removeStarButton.setAlignmentX(0.0f);

            Box buttonBox = Box.createHorizontalBox();
            buttonBox.add(Box.createHorizontalGlue());
            buttonBox.add(new JButton(doneAction));
            buttonBox.add(Box.createHorizontalStrut(6));
            buttonBox.add(new JButton(cancelAction));

            Box box = (GradientBox) getContent();
            box.add(removeStarButton);
            box.add(Box.createVerticalStrut(6));
            box.add(new JLabel("Description:"));
            box.add(desc);
            box.add(Box.createVerticalStrut(6));
            box.add(new JLabel("Shorthand:"));
            box.add(shorthand);
            box.add(Box.createVerticalStrut(6));
            box.add(buttonBox);
            box.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            box.setBackground(new Color(220, 230, 250));
            box.setOpaque(true);
            for (Component comp : box.getComponents()) {
                ((JComponent) comp).setAlignmentX(0f);
            }

            populateSavedSearchFields();

            addAncestorAcceleratorKey(box, doneAction, KeyEvent.VK_ENTER);
            addAncestorAcceleratorKey(box, cancelAction, KeyEvent.VK_ESCAPE);
            removeSearchAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_R);

            // Set the action for (e.g.) when the user clicks outside the callout.
            addDismissListener(doneAction);
        }

        @Override
        public void showAt(int x, int y) {
            super.showAt(x, y);
            SwingUtilities.invokeLater(() -> desc.requestFocusInWindow());
        }

        private AbstractAction createDoneAction() {
            return new AbstractAction("Done") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    applyChanges();
                }
            };
        }

        private Action createCancelAction() {
            return new AbstractAction("Cancel") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dismiss();
                }
            };
        }

        private Action createRemoveSearchAction() {
            return new AbstractAction("Remove saved search") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    clearSavedSearch();
                    dismiss();
                }
            };
        }

        private void populateSavedSearchFields() {
            SavedSearch ss = comboModel.findSearch(getText());
            shorthand.setText((ss == null || ss.alias == null) ? "" : ss.alias);
            desc.setText((ss == null || ss.name == null) ? "" : ss.name);
        }

        private void addAncestorAcceleratorKey(
                JComponent ancestor, Action action, int keyCode)
        {
            ancestor.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(keyCode, 0),
                action.getValue(Action.NAME));
            ancestor.getActionMap().put(action.getValue(Action.NAME), action);
        }

        protected void applyChanges() {
            SavedSearch ss = new SavedSearch(getText(), shorthand.getText(), desc.getText());
            comboModel.updateSearch(ss);
            dismiss();
        }

        @Override
        public void dismiss() {
            super.dismiss();
            getParent().requestFocusInWindow();
        }
    }

    private static final Color MID_SHADOW = new Color(199, 202, 207);
    private static final Color LIGHTER_SHADOW = new Color(203, 203, 204);
    private static final Color DARK_BORDER = new Color(141, 142, 143);

    private static enum StarIcons {
        NORMAL("res/star_grey.png"), SELECTED("res/star_yellow.png"), ROLLOVER(
                "res/star_grey_roll.png"), SELECTED_ROLLOVER(
                        "res/star_yellow_roll.png"), PRESSED("res/star_yellow_pressed.png");

        private ImageIcon icon;

        private StarIcons(String resourcePath) {
            icon = createImageIcon(resourcePath);
        }

        /** Returns an ImageIcon, or null if the path was invalid. */
        protected static ImageIcon createImageIcon(String path) {
            URL imgURL = SearchComboEditor.class.getResource(path);
            return new ImageIcon(imgURL);
        }

        public Icon getIcon() {
            return icon;
        }
    }

    private static final int iconWidth;
    private static final int iconHeight;

    static {
        Icon normal = StarIcons.NORMAL.getIcon();
        iconHeight = normal.getIconHeight();
        iconWidth = normal.getIconWidth();
    }


    private final class ColouredFieldView extends FieldView {
        Segment seg = new Segment();
        private ColouredFieldView(Element elem) {
            super(elem);
        }

        @Override
        protected int drawSelectedText(Graphics g, int x, int y, int p0, int p1)
                throws BadLocationException {

            // Although this loop SEEMS pointless (since the colour is constant), it is necessary to
            // produce precisely the same character spacing calculation as the unselected-text
            // code-path. This has implications when running on displays with non-integer scaling
            // factors, notably when running on JDK >= 9. This code forces the use of JDK-8 APIs
            // whereas a not-overridden version would use floating-point offset methods.
            int t = 0;
            int prev = p0;
            while (t < tokenInfo.size() && prev < p1) {
                SearchSpan span = tokenInfo.get(t++);
                int len = Math.min(span.end, p1) - prev;
                if (len > 0) {
                    x = super.drawSelectedText(g, x, y, prev, prev + len);
                    prev = span.end;
                }
            }
            return x;
        }

        // NB: coded to JDK-8 style APIs, which operate upon int.
        // TODO: switch to the 'float' APIs for better HiDPI support when moving to modern Java
        @Override
        protected int drawUnselectedText(Graphics g, int x, int y, int p0, int p1)
                throws BadLocationException {

            Document doc = getDocument();
            int t = 0;
            int prev = p0;
            while (t < tokenInfo.size() && prev < p1) {
                SearchSpan span = tokenInfo.get(t++);
                int len = Math.min(span.end, p1) - prev;
                if (len > 0) {
                    g.setColor(colorFor(span));
                    doc.getText(prev, len, seg);
                    x = Utilities.drawTabbedText(seg, x, y, g, this, prev);
                    prev = span.end;
                }
            }

            // This goes on to determine the caret colour! ("temporal coupling" smell)
            g.setColor(SearchComboEditor.this.getForeground());
            return x;
        }

        private Color colorFor(SearchSpan span) {
            switch (span.kind) {
                case ALIAS: return Color.ORANGE.darker();
                case FIELD: return Color.CYAN.darker().darker();
                case NEGATE: return Color.RED.darker();
                case TEXT: return Color.BLACK;
            }
            throw new AssertionError();
        }
    }

    /**
     * Despite TextFields not supporting EditorKits, StyleDocument etc, we still want to be able to
     * colour-code the search field, keep TextFields's behaviour (e.g. text scrolls, not wraps)
     * but without an entire UI implementation.
     *
     *<p>We presently assume Synth/Nimbus L&F, which will unfortunately add work to switch L&Fs later.
     * A good looking solution to this looks to be to use the ByteBuddy library to dynamically subclass
     * the UI object, and 'intercept' its create method. However let's not add another dependency yet.
     */
    final class ColouredFieldUI extends SynthTextFieldUI {

        @Override
        public View create(Element elem) {
            View v = super.create(elem);
            if (v instanceof FieldView) {
                return new ColouredFieldView(elem);
            }
            return v;
        }
    }

    @Override
    public void setUI(TextUI newUI) {
        if (newUI instanceof SynthTextFieldUI) {
            newUI = new ColouredFieldUI();
        }
        super.setUI(newUI);
    }

    private class Listener implements MouseListener, MouseMotionListener {
        private Boolean lastOverStar = null;

        @Override
        public void mouseMoved(MouseEvent e) {

            Boolean overStar = isOverStar(e.getX(), e.getY());
            starModel.setRollover(overStar);

            // Avoid extra work updating cursor
            if (!overStar.equals(lastOverStar)) {
                lastOverStar = overStar;
                int cursor = overStar ? Cursor.DEFAULT_CURSOR : Cursor.TEXT_CURSOR;
                ((Component) e.getSource()).setCursor(Cursor.getPredefinedCursor(cursor));
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (Boolean.TRUE.equals(lastOverStar) && !isOverStar(e.getX(), e.getY())) {
                exitStar();
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
            exitStar();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (isMouseEventRelevant(e)) {
                starModel.setPressed(true);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (isMouseEventRelevant(e) && starModel.isPressed()) {
                if (starModel.isSelected()) {
                    showCallout();
                } else {
                    quickSaveSearch();
                }
            }
            starModel.setPressed(false);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
        }

        private boolean isMouseEventRelevant(MouseEvent e) {
            return SwingUtilities.isLeftMouseButton(e) && isOverStar(e.getX(), e.getY());
        }

        private void exitStar() {
            starModel.setRollover(false);
            starModel.setPressed(false);
            lastOverStar = null;
        }
    }

    private ButtonModel starModel;
    private final int extraLeft = 4;
    private final int extraRight = 6 + iconWidth;
    private SavedSearchCallout callout;
    private final SearchComboBoxModel comboModel;
    List<SearchSpan> tokenInfo = new ArrayList<>();

    public SearchComboEditor(SearchComboBoxModel comboModel, String value, int n) {
        super(new CustomUndoPlainDocument(), value, n);
        this.comboModel = comboModel;

        // A crack an mimicking a Nimbus combo editor's border, kind of.
        // See also paintComponent(...)
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(2, 2, 2, 0),
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DARK_BORDER),
                BorderFactory.createEmptyBorder(0, extraLeft, 0, extraRight))));

        starModel = new DefaultButtonModel();
        starModel.addChangeListener(e -> repaint());

        getDocument().addDocumentListener(DocUtils.newOnAnyEventListener(this::updateFromDocument));

        Listener ml = new Listener();
        addMouseListener(ml);
        addMouseMotionListener(ml);
    }

    protected void updateFromDocument() {
        String text = getText();
        boolean found = comboModel.findSearch(text) != null;
        starModel.setSelected(found);
        tokenInfo = SearchSpan.parse(
                (SortedSet<String>) comboModel.getShorthandAliases().keySet(),
                text);
    }

    public void quickSaveSearch() {
        SavedSearch ss = new SavedSearch(getText());
        comboModel.updateSearch(ss);
    }

    public void clearSavedSearch() {
        SavedSearch ss = comboModel.findSearch(getText());
        if (ss != null) {
            comboModel.removeElement(ss);
        }
        comboModel.setSelectedItem(ss);
    }

    // workaround for 4530952
    @Override
    public void setText(String s) {
        if (getText().equals(s)) {
            return;
        }
        super.setText(s);
    }

    @Override
    protected void paintComponent(Graphics g1) {
        Graphics2D g = (Graphics2D) g1.create();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Insets in = getInsets();
        int y = in.top;
        int left = in.left - extraLeft;
        int right = getWidth() - in.right + extraRight;
        int bottom = getHeight() - in.bottom - 1;

        // All this faffing-around may be more reason to ditch Combo and use a simple
        // text field
        g.setColor(getBackground());
        g.fillRect(right - 3, in.top, right, getHeight() - in.bottom - in.top);
        super.paintComponent(g);
        g.setColor(MID_SHADOW);

        g.drawLine(left, y, right, y);
        g.setColor(LIGHTER_SHADOW);
        ++y;
        g.drawLine(left, y, right, y);

        g.setColor(MID_SHADOW);
        g.drawLine(left, in.top, left, bottom);
        g.drawLine(left, bottom, right, bottom);

        Icon star = getCurrentStarIcon();
        star.paintIcon(this, g, getStarX(), getStarY());
        g.dispose();
    }

    private Icon getCurrentStarIcon() {
        if (starModel.isPressed()) {
            return StarIcons.PRESSED.icon;
        }
        SearchComboEditor.StarIcons st = starModel.isRollover() ?
            (starModel.isSelected() ? StarIcons.SELECTED_ROLLOVER : StarIcons.ROLLOVER) :
            (starModel.isSelected() ? StarIcons.SELECTED : StarIcons.NORMAL);
        return st.getIcon();
    }

    public int getStarX() {
        // The star is within the editor's border.
        return getWidth() - getInsets().right + 1;
    }

    public int getStarY() {
        Insets insets = getInsets();
        int space = getHeight() - insets.top - insets.bottom - iconHeight;
        return insets.top + space / 2;
    }

    public int getStarWidth() {
        return iconWidth;
    }

    public int getStarHeight() {
        return iconHeight;
    }

    public boolean isOverStar(int x, int y) {

        // Y ignored: don't leave "cracks" that don't work above & below the star.
        return x >= getStarX();
    }

    public void showCallout() {
        if (callout != null) {
            callout.dismiss();
        }
        JFrame wind = (JFrame) SwingUtils.getWindowForComponent(this);
        Point pt = pointToShowCallout(wind);
        callout = new SavedSearchCallout(wind);
        callout.showAt(pt.x, pt.y);
    }

    private Point pointToShowCallout(JFrame wind) {
        int x = getStarX() + getStarWidth() / 2;
        int y = getStarY() + getStarHeight();
        Point pt = SwingUtilities.convertPoint(this, x, y, wind.getContentPane());
        return pt;
    }
}
