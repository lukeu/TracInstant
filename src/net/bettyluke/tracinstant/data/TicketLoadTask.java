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
        
package net.bettyluke.tracinstant.data;

import java.util.Collections;
import java.util.List;

import javax.swing.SwingWorker;

import net.bettyluke.tracinstant.data.TicketLoadTask.Update;
import net.bettyluke.util.ObjectUtils;

public abstract class TicketLoadTask extends SwingWorker<Void, Update> {
    
    /** 
     * A mutually-exclusive structure (no 'union' in Java) to pass status updates and 
     * generated ticket data from the publish() method (on background thread) to 
     * the process() method (on the EDT).
     * <p>
     * If (ticketProvider != null) then extract the tickets from it, otherwise process the
     * status messages. 
     */
    public static class Update {
        public final TicketProvider ticketProvider;
        public final String summaryMessage;
        public final String detailMessage;

        public Update(TicketProvider tp) {
            this.detailMessage = null;
            this.summaryMessage = null;
            this.ticketProvider = tp;
        }

        public Update(String summaryMessage, String detailMessage) {
            this.detailMessage = detailMessage;
            this.summaryMessage = summaryMessage;
            this.ticketProvider = null;
        }

        /** A special update meaning "job complete" */
        public Update() {
            this.detailMessage = null;
            this.summaryMessage = null;
            this.ticketProvider = null;
        }
        
        @Override
        public int hashCode() {
            return ObjectUtils.hash(detailMessage, summaryMessage, ticketProvider);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Update)) {
                return false;
            }
            Update other  = (Update) obj;
            return ObjectUtils.equals(ticketProvider, other.ticketProvider)
                && ObjectUtils.equals(summaryMessage, other.summaryMessage) 
                && ObjectUtils.equals(detailMessage, other.detailMessage);
        }
    }
    
    private Update statusUpdate = null;
    
    protected Runnable doneCallback = null;
    
    protected final TicketTableModel tableModel;
    
    
    public TicketLoadTask(TicketTableModel tableModel) {
        this.tableModel = tableModel;
    }
    
    @Override
    protected void process(List<Update> chunks) {
        if (isCancelled()) {
            return;
        }
        for (Update newUpdate : chunks) {
            Update oldStatus = statusUpdate;
            statusUpdate = newUpdate;
            if (ObjectUtils.equals(oldStatus, statusUpdate)) {
                continue;
            }
            firePropertyChange("status", oldStatus, statusUpdate);
        }
    }
    
    @Override
    protected void done() {
        super.done();

        if (doneCallback != null) {
            doneCallback.run();
            doneCallback = null;
        }

        /* A final null status message is used to indicate completion. */
        process(Collections.<Update>singletonList(new Update()));
    }

    public void executeWithNotification(Runnable runnable) {
        doneCallback = runnable;
        execute();
    }
}
