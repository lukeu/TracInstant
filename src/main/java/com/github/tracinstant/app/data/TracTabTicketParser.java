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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import au.com.bytecode.opencsv.CSVReader;

public class TracTabTicketParser {

    private final BufferedReader reader;

    public static TicketProvider parse(Reader reader) throws IOException, InterruptedException {
        TracTabTicketParser parser = new TracTabTicketParser(reader);
        return parser.parseFile();
    }

    private TracTabTicketParser(Reader reader) {
        this.reader = new BufferedReader(reader);
    }

    private TicketProvider parseFile() throws IOException, InterruptedException {

        // Tab delimited reader with no un-escaping of '\' characters.
        CSVReader csvReader = new CSVReader(reader, '\t', '"', '\0');
        try {
            String[] headings = csvReader.readNext();
            if (headings == null) {
                throw new IOException("Empty input given");
            }

            final TracTabResult result;
            try {
                result = new TracTabResult(headings);
            } catch (RuntimeException ex) {
                throw new IOException(ex);
            }

            String[] fields;
            while ((fields = csvReader.readNext()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                result.addTicketFromFields(fields);
            }
            return result;
        } finally {
            csvReader.close();
        }
    }
}
