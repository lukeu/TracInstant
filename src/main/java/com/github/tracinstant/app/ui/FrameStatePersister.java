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

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import com.github.tracinstant.app.prefs.TracInstantProperties;

public class FrameStatePersister extends ComponentAdapter {
    private final String maximisedKey;
    private final String boundsKey;
    private final Frame frame;

    public FrameStatePersister(String basePropertyKey, Frame frame) {
        this.maximisedKey = basePropertyKey + ".Maximised";
        this.boundsKey = basePropertyKey + ".Bounds";
        this.frame = frame;
    }

    public void startListening() {
        frame.addComponentListener(this);
    }

    public void stopListening() {
        frame.removeComponentListener(this);
    }

    public void saveFrameState() {
        if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            Rectangle bounds = frame.getBounds();
            TracInstantProperties.get().putString(boundsKey, bounds.toString());
        }
        boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        TracInstantProperties.get().putBoolean(maximisedKey, max);
    }

    public void restoreFrameState() {
        Rectangle bounds = TracInstantProperties.getRectangle(boundsKey, null);
        if (bounds == null) {
            frame.setPreferredSize(new Dimension(900, 650));
            frame.pack();
        } else {
            adjustBoundsToDisplay(bounds);
            frame.setBounds(bounds);
        }
        if (TracInstantProperties.get().getBoolean(maximisedKey, false)) {
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    /**
     * Cater for a previous run being on a monitor which is now detached. Windows 11 in particular
     * offers no means to 'rescue' a window placed completely out of bounds - the windowing keyboard
     * shortcuts don't work, and actions like restore/maximise have been removed from the UI!
     */
    private static void adjustBoundsToDisplay(Rectangle bounds) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle maxBounds = ge.getMaximumWindowBounds();
        if (bounds.x + bounds.width < maxBounds.x + 10
                || bounds.x > maxBounds.x + maxBounds.width - 10) {
            bounds.x = maxBounds.x;
        }
        if (bounds.y + bounds.width < maxBounds.y + 10
                || bounds.y > maxBounds.y + maxBounds.width - 10) {
            bounds.y = maxBounds.y;
        }
    }

    // ---------------------------------------------------------------------------------

    @Override
    public void componentResized(ComponentEvent e) {
        assert frame == e.getComponent();
        saveFrameState();
    }
}
