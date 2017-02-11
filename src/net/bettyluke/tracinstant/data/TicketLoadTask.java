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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.SwingWorker;

import net.bettyluke.tracinstant.data.TicketLoadTask.Update;

/**
 * @param <T> The result of <code>doInBackground</code> and <code>get</code> is a list of
 *            ticket modification dates, as extracted from all tickets loaded. This will be
 *            processed in the 'done' method so it is not required for client code to access them
 */
public abstract class TicketLoadTask extends SwingWorker<List<String>, Update> {

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
            return Objects.hash(detailMessage, summaryMessage, ticketProvider);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Update)) {
                return false;
            }
            Update other = (Update) obj;
            return Objects.equals(ticketProvider, other.ticketProvider)
                    && Objects.equals(summaryMessage, other.summaryMessage)
                    && Objects.equals(detailMessage, other.detailMessage);
        }
    }

    private Update statusUpdate = null;

    protected Runnable doneCallback = null;

    protected final SiteData site;

    public TicketLoadTask(SiteData site) {
        this.site = site;
    }

    @Override
    protected void process(List<Update> chunks) {
        if (isCancelled()) {
            return;
        }
        for (Update newUpdate : chunks) {
            Update oldStatus = statusUpdate;
            statusUpdate = newUpdate;
            if (Objects.equals(oldStatus, statusUpdate)) {
                continue;
            }
            firePropertyChange("status", oldStatus, statusUpdate);
        }
    }

    @Override
    protected void done() {
        super.done();

        try {
            List<String> strings = get();
            site.setLastModifiedTicketTime(strings);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        /* A final null status message is used to indicate completion. */
        process(Collections.<Update>singletonList(new Update()));

        if (doneCallback != null) {
            doneCallback.run();
            doneCallback = null;
        }
    }

    public void executeWithNotification(Runnable runnable) {
        doneCallback = runnable;
        execute();
    }

    protected static List<String> extractModificationDates(Collection<Ticket> tickets) {
        return streamChangeTimes(tickets).collect(Collectors.toList());
    }

    protected static Stream<String> streamChangeTimes(Collection<Ticket> tickets) {
        return tickets.stream()
                .map(ticket -> ticket.getValue("changetime"))
                .filter(Objects::nonNull);
    }
}
