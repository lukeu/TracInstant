package com.github.tracinstant.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;

public class VerticallyScrollingPanel<T extends Component & Scrollable> extends JPanel
        implements Scrollable {

    public static <T extends Component & Scrollable> VerticallyScrollingPanel<T> create(T d) {
        return new VerticallyScrollingPanel<>(d);
    }

    private VerticallyScrollingPanel(T delegate) {
        super(new BorderLayout());
        add(delegate);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getDelegate().getPreferredScrollableViewportSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return getDelegate().getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return getDelegate().getScrollableBlockIncrement(visibleRect, orientation, direction);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    @Override
    public BorderLayout getLayout() {
        return (BorderLayout) super.getLayout();
    }

    @SuppressWarnings("unchecked")
    T getDelegate() {
        return (T) getLayout().getLayoutComponent(this, BorderLayout.CENTER);
    }
}
