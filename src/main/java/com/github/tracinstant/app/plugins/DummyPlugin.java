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

package com.github.tracinstant.app.plugins;

import javax.swing.JComponent;

import com.github.tracinstant.app.data.Ticket;

public class DummyPlugin extends ToolPlugin {

    private final String m_DisplayName;

    public DummyPlugin(String displayName) {
        m_DisplayName = displayName;
    }

    @Override
    public JComponent initialise(TicketUpdater updater) {
        return null;
    }

    @Override
    public void ticketViewUpdated(Ticket[] inView, Ticket[] selected) {
    }

    @Override
    public String toString() {
        return m_DisplayName;
    }
}
