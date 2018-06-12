package com.github.tracinstant.app.plugins;

import javax.swing.JComponent;

import com.github.tracinstant.app.data.Ticket;

public abstract class ToolPlugin {

    public abstract JComponent initialise(TicketUpdater updater);

    public void shown() {}
    public void hidden() {}

    public void ticketViewUpdated(Ticket[] inView, Ticket[] selected) {}

    /** Must return a display name, for use in the user interface. */
    @Override
    public abstract String toString();
}
