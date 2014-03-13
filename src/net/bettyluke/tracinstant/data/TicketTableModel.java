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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.table.AbstractTableModel;

/**
 * A bit more that just a "Table" model - this is pretty much the data-model of the whole 
 * app. Perhaps this could be targeted with future refactoring.
 */
public class TicketTableModel extends AbstractTableModel {

    // TODO: Remove "title" at the parsing level? e.g. for memory and search speed?
    private final Set<String> excludedFields =
        new TreeSet<String>(Arrays.asList("description", "link", "title", "changetime"));

    private static final int TICKET_NUMBER_COLUMN = 0;

    /** Tickets by row. */
    private Ticket[] tickets = new Ticket[0];
    
    /** All fields found in any of the tickets. */
    private SortedSet<String> knownFields = new TreeSet<String>();
    
    private SortedSet<String> userFields = new TreeSet<String>();
    
    /** Columns currently in use. */
    private String[] shownColumns = new String[0];
    
    public SortedSet<String> getUserFields() {
        return Collections.unmodifiableSortedSet(userFields);
    }
    
    public SortedSet<String> getAllFields() {
        return Collections.unmodifiableSortedSet(knownFields);
    }
    
    public Set<String> getExcludedFields() {
        return Collections.unmodifiableSet(excludedFields);
    }
    
    public List<Ticket> getTicketsWithAnyField(Collection<String> fields) {
        List<Ticket> result = new ArrayList<Ticket>(tickets.length);
        for (Ticket t : tickets) {
            Ticket copy = new Ticket(t.getNumber());
            for (String f : fields) {
                String value = t.getValue(f);
                if (value != null) {
                    copy.putField(f, value);
                }
            }
            if (!copy.getFieldNames().isEmpty()) {
                result.add(copy);
            }
        }
        return result;
    }
    
    public void mergeTicketsAsHidden(Collection<Ticket> newTickets) {
        mergeTicketFieldsInto(newTickets, excludedFields);
        mergeTickets(newTickets);
    }
    
    public void mergeTickets(Collection<Ticket> newTickets) {
        if (newTickets.isEmpty()) {
            return;
        }
        
        int oldRowCount = getRowCount();
        
        // Create a temporary map for sorting and lookup by ID
        Map<Integer, Ticket> ticketMap = getTicketsAsMap();
        for (Ticket newTicket : newTickets) {
            mergeIntoMap(ticketMap, newTicket);
        }
        
        // Update class members.
        tickets = ticketMap.values().toArray(new Ticket[ticketMap.size()]);
        mergeTicketFieldsInto(newTickets, knownFields);
        
        String[] oldColumns = shownColumns;
        shownColumns = determineUsedColumns();
        if (!Arrays.equals(oldColumns, shownColumns)) {
            fireTableStructureChanged();
        } else {
            int newRowCount = getRowCount();
            if (newRowCount > oldRowCount) {
                fireTableRowsInserted(oldRowCount, newRowCount - 1);
            }
            fireTableRowsUpdated(0, oldRowCount - 1);
        }
    }

    private Map<Integer, Ticket> getTicketsAsMap() {
        Map<Integer, Ticket> ticketMap = new TreeMap<Integer, Ticket>();
        for (Ticket ticket : tickets) {
            ticketMap.put(ticket.getNumber(), ticket);
        }
        return ticketMap;
    }

    private static void mergeIntoMap(Map<Integer, Ticket> ticketMap, Ticket t) {
        int id = t.getNumber();
        Ticket existing = ticketMap.get(id);
        if (existing == null) {
            ticketMap.put(id, new Ticket(t));
        } else {
            existing.setFieldsFromTicket(t);
        }
    }
    
    private void mergeTicketFieldsInto(Collection<Ticket> newTickets, Set<String> set) {
        for (Ticket ticket : newTickets) {
            set.addAll(ticket.getFieldNames());
        }
    }
    
    private String[] determineUsedColumns() {
        // TODO: Stub! Make individually selectable. (A custom column model?)
        Set<String> fields = new TreeSet<String>(knownFields);
        fields.removeAll(excludedFields);
        fields.add("#");
        return fields.toArray(new String[fields.size()]);
    }
    
    @Override
    public int getRowCount() {
        return tickets.length;
    }

    @Override
    public int getColumnCount() {
        return shownColumns.length;
    }
    
    @Override
    public String getColumnName(int column) {
        return shownColumns[column];
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == TICKET_NUMBER_COLUMN) {
            return Integer.class;
        }
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == TICKET_NUMBER_COLUMN) {
            return getTicket(rowIndex).getNumber();
        }
        String columnTag = getColumnName(columnIndex);
        return getTicket(rowIndex).getValue(columnTag);
    }

    public Ticket getTicket(int rowIndex) {
        return tickets[rowIndex];
    }

    /**
     * @return a snapshot of the tickets. (The array is copied, but Tickets are mutable.)
     *         The tickets are sorted by ticket ID.
     */
    public Ticket[] getTickets() {
        return Arrays.copyOf(tickets, tickets.length);
    }

    /** NB: Slow! */
    public Ticket findTicketByID(int ticketId) {
        for (Ticket t : tickets) {
            if (t.getNumber() == ticketId) {
                return t;
            }
        }
        return null;
    }
    
    public void clear() {
        userFields.clear();
        knownFields.clear();
        shownColumns = new String[0];
        fireTableStructureChanged();
        tickets = new Ticket[0];
        fireTableDataChanged();
    }

    public void addUserField(String fieldName) {
        userFields.add(fieldName);
        
    }

    public void removeUserField(String fieldName) {
        userFields.remove(fieldName);
    }
}

