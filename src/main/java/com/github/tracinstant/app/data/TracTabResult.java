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

package com.github.tracinstant.app.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TracTabResult implements TicketProvider {

    private final String[] m_Headings;
    private Map<Integer, Ticket> m_Tickets = new LinkedHashMap<>();

    public TracTabResult(String[] headings) {
        if (headings == null) {
            throw new NullPointerException();
        }
        if (headings.length < 1 || !headings[0].endsWith("id")) {
            throw new IllegalArgumentException(
                "Unsupported headings in tab separated data: " +
                Arrays.toString(headings));
        }
        m_Headings = headings;
    }

    public void addTicketFromFields(String[] values) {
        int len = values.length;
        if (len > m_Headings.length) {
            System.out.println("Mismatched line (stray Tab characters?): " +
                Arrays.toString(values));
            return;
        }
        if (len < m_Headings.length) {
            values = Arrays.copyOf(values, m_Headings.length);
            Arrays.fill(values, values.length, m_Headings.length, "");
        }

        try {
            Ticket ticket = new Ticket(Integer.parseInt(values[0]));
            for (int i = 1; i < len; i++) {
                ticket.setOrMergeField(m_Headings[i], values[i]);
            }
            m_Tickets.put(ticket.getNumber(), ticket);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid ticket, start of line not an ID:" + values[0]);
        }
    }

    @Override
    public List<Ticket> getTickets() {
        return new ArrayList<>(m_Tickets.values());
    }
}
