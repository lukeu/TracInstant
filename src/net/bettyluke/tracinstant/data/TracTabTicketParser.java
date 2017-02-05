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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;

public class TracTabTicketParser {

    private final BufferedReader reader;

    private Map<String, String> stringCache = new HashMap<>();

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
                result.addTicketFromFields(cacheStrings(fields));
            }
            return result;
        } finally {
            csvReader.close();
        }
    }

    /**
     * Ensures all strings are cached, It is anticipated that many strings will appear
     * multiple times, so we try not to duplicate them in memory.
     * <p>
     * It's like String.intern() but we are in control of the cache.
     */
    private String[] cacheStrings(String[] strings) {
        for (int j = 0; j < strings.length; j++) {
            String orig = strings[j];
            String cached = stringCache.get(strings[j]);
            if (cached == null) {

                // Intentionally create unique strings.
                cached = new String(orig);
                stringCache.put(cached, cached);
            }
            strings[j] = cached;
        }
        return strings;
    }
}
