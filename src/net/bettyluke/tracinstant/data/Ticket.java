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
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class Ticket {
    
    private Map<String, String> m_Fields = new TreeMap<String, String>();
    
    private final int m_Number;
    
    public Ticket(int Number) {
        m_Number = Number;
    }
    
    public Ticket(Ticket original) {
        m_Number = original.m_Number;
        mergeFields(original);
    }
    
    /** Merge the given ticket's data into this one. */
    public void mergeFields(Ticket ticket) {
        if (m_Number != ticket.m_Number) {
            System.err.println("Ticket numbers don't match");
        }
        
        for (Entry<String, String> field : ticket.m_Fields.entrySet()) {
            m_Fields.put(field.getKey(), field.getValue());
        }
    }

    public int getNumber() {
        return m_Number;
    }
    
    @Override
    public String toString() {
        return "Ticket " + m_Number + ", " + super.toString();
    }

    public void putField(String fieldName, String value) {
        if (value == null) {
            m_Fields.remove(fieldName);
        } else {
            m_Fields.put(fieldName, value);
        }
    }
    
    public void remove(String fieldName) {
        m_Fields.remove(fieldName);
    }

    public void appendField(String fieldName, String value) {
        String existing = m_Fields.get(fieldName);
        m_Fields.put(fieldName, (existing == null) ? value : existing + "�\n" + value);
    }

    public Collection<String> getFieldNames() {
        return Collections.unmodifiableSet(m_Fields.keySet());
    }

    public String getValue(String fieldName) {
        return m_Fields.get(fieldName);
    }
}
