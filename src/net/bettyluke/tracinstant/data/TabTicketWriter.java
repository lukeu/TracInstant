/*
 * Copyright 2011 Luke Usherwood.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


import au.com.bytecode.opencsv.CSVWriter;

public class TabTicketWriter {
    
    private PrintWriter m_Writer;
    private boolean m_ContentOnLine = false;

    public static void write(Writer writer, Set<String> fieldsSet, List<Ticket> tickets) {
        
        List<String> fields = new ArrayList<String>(); 
        fields.add("id");
        fields.addAll(fieldsSet);

        CSVWriter csvWriter = new CSVWriter(writer, '\t');
        csvWriter.writeNext(fields.toArray(new String[fields.size()]));
        for (Ticket subTicket : tickets) {
            String[] values = new String[fields.size()];
            
            // TODO: Should the table model just expose the field via '#'?
            // (It's internally stored as an int *mainly* so that JTable renders
            // it right-aligned! But publically, it should be a searchable String field.)
            values[0] = Integer.toString(subTicket.getNumber());
            int i = 1;
            for (String f : fieldsSet) {
                values[i++] = subTicket.getValue(f);
            }
            csvWriter.writeNext(values);
        }
        try {
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public TabTicketWriter(Writer writer) {
        m_Writer = new PrintWriter(new BufferedWriter(writer));
    }

    public void writeLine(Collection<String> items) {
        for (String i : items) {
            write(i);
        }
        endLineIfNotEmpty();
        m_ContentOnLine = false;
    }

    public void write(String string) {
        if (m_ContentOnLine) {
            m_Writer.append('\t');
        }
        m_Writer.append(escape(string));
        m_ContentOnLine = true;
    }

    private CharSequence escape(String string) {
        return "\"" + string + "\"";
    }

    public void endLineIfNotEmpty() {
        if (m_ContentOnLine) {
            m_Writer.println();
            m_ContentOnLine = false;
        }
    }
    
    public void close() {
        m_Writer.close();
    }
}
