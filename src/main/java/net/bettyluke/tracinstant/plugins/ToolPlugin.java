package net.bettyluke.tracinstant.plugins;

import javax.swing.JComponent;

import net.bettyluke.tracinstant.data.Ticket;

public abstract class ToolPlugin {

    public abstract JComponent initialise(TicketUpdater updater);

    public void shown() {}
    public void hidden() {}

    public void ticketViewUpdated(Ticket[] inView, Ticket[] selected) {}

    /** Must return a display name, for use in the user interface. */
    @Override
    public abstract String toString();
}
