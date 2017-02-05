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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Just a few heading-like fields, plus ticket data. */
public class TracXmlResult implements TicketProvider {

    private Map<Integer, Ticket> m_Tickets = new LinkedHashMap<>();
    private String m_Title = null;
    private String m_Link = null;
    private String m_Description = null;

    public void addTicket(Ticket ticket) {
        m_Tickets.put(ticket.getNumber(), ticket);
    }

    @Override
    public List<Ticket> getTickets() {

        // The Ticket objects are still mutable. Never mind.
        return new ArrayList<>(m_Tickets.values());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Title=" + m_Title + ", Link=" + m_Link);
        sb.append("tickets=[");
        for (Ticket ticket : m_Tickets.values()) {
            sb.append("\n  ").append(ticket.toString());
        }
        sb.append("]");
        return sb.toString();
    }

    public String getTitle() {
        return m_Title;
    }

    public void setTitle(String title) {
        m_Title = title;
    }

    public String getLink() {
        return m_Link;
    }

    public void setLink(String link) {
        m_Link = link;
    }

    public String getDescription() {
        return m_Description;
    }

    public void setDescription(String description) {
        m_Description = description;
    }
}
