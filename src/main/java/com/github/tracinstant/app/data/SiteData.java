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

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.github.tracinstant.app.prefs.TracInstantProperties;

public class SiteData {

    static final String TABULAR_CACHE_FILE = "SiteCache_Tabular.txt";
    static final String HIDDEN_FIELDS_CACHE_FILE = "SiteCache_Hidden.txt";

    private final TicketTableModel m_TableModel = new TicketTableModel();
    private String dateTimeFormatString = null;
    private List<DateTimeFormatter> dateTimeFormats = new ArrayList<>();
    private String lastModifiedTicketTime;
    private boolean hasConnected = false;

    public SiteData() {
        setDateFormat(TracInstantProperties.get().getValue("SiteDateFormat"));
    }

    public void saveState() {
        SortedSet<String> userFields = m_TableModel.getUserFields();
        saveTicketData(makeUserFieldsFileName(), userFields);

        if (dateTimeFormatString != null) {
            TracInstantProperties.get().putString("SiteDateFormat", dateTimeFormatString);
        }

        if (TracInstantProperties.getUseCache()) {
            SortedSet<String> fields = new TreeSet<>(m_TableModel.getAllFields());
            fields.removeAll(m_TableModel.getExcludedFields());
            fields.removeAll(userFields);
            saveTicketData(TABULAR_CACHE_FILE, fields);

            fields = new TreeSet<>(m_TableModel.getExcludedFields());
            fields.removeAll(userFields);
            saveTicketData(HIDDEN_FIELDS_CACHE_FILE, fields);
        }
    }

    public void loadUserData() {
        try {
            TicketProvider tp = loadTicketData(makeUserFieldsFileName());
            if (tp != null) {
                m_TableModel.mergeTicketsAsHidden(tp.getTickets());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isOkToUseCachedTickets() {
        try {
            return TracInstantProperties.getUseCache()
                && TracInstantProperties.getURL() != null
                && Files.isReadable(getAppDataDir().resolve(TABULAR_CACHE_FILE))
                && Files.isReadable(getAppDataDir().resolve(HIDDEN_FIELDS_CACHE_FILE));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public TicketTableModel getTableModel() {
        return m_TableModel;
    }

    // NB: Gets called from a worker thread as well as foreground thread.
    // TODO: Separate loading/building out from data structures. Move into a TicketLoader
    // class to sequence/coordinate loading all the various bits (see main TODO document).
    static TicketProvider loadTicketData(String fileName) throws InterruptedException {
        try {
            Path file = getAppFileForReading(fileName);
            if (file != null) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return TracTabTicketParser.parse(reader);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Path getAppFileForReading(String simpleFileName) throws IOException {
        Path dataDir = getAppDataDir();
        if (!Files.isDirectory(dataDir)) {
            return null;
        }
        Path file = dataDir.resolve(simpleFileName);
        if (!Files.exists(file)) {
            return null;
        }
        return file;
    }

    private static Path getAppDataDir() throws IOException {
        return TracInstantProperties.get().getAppDataDirectory();
    }

    private void saveTicketData(String fileName, Set<String> fields) {
        try {
            Path dataDir = getAppDataDir();
            if (!Files.isDirectory(dataDir) && !dataDir.toFile().mkdirs()) {
                throw new IOException("Directory could not be created: " + dataDir);
            }
            Path dataFile = dataDir.resolve(fileName);
            List<Ticket> tickets = m_TableModel.getTicketsWithAnyField(fields);
            TabTicketWriter.write(
                    Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8), fields, tickets);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reset() {
        deleteCachedDataFiles();
        m_TableModel.clear();
        lastModifiedTicketTime = null;
        hasConnected = false;
    }

    private void deleteCachedDataFiles() {
        deleteAppFile(TABULAR_CACHE_FILE);
        deleteAppFile(HIDDEN_FIELDS_CACHE_FILE);
    }

    private void deleteAppFile(String name) {
        try {
            Path file = getAppDataDir().resolve(name);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String makeUserFieldsFileName() {
        String url = TracInstantProperties.getURL();
        return extractAsciiCharacters(url) + "__LocalFields.txt";
    }

    private static String extractAsciiCharacters(String url) {
        int colon = url.indexOf(':');

        // We strip (e.g.) http, but LEAVE the colon ':'
        if (colon >= 0 && colon <= 8) {
            url = url.substring(colon);
        }
        String replaced = url.replaceAll("[^0-9a-zA-Z_]+", "-");

        // Now strip the (original) ':' and the zero or more '/' characters, which have
        // all been converted to a single '-'.
        return replaced.substring(1);
    }

    public void setDateFormat(String dateFormat) {
        dateTimeFormatString = dateFormat;
        dateTimeFormats.clear();
        if (dateFormat != null) {
            String timeFormat12 = "[','][';'][' '][['t']h:m[:s][ ]a]";
            String timeFormat24 = "[','][';'][' '][['t']HH:mm[:ss]]";
            dateTimeFormats.add(dateTimeBuilder(dateFormat, timeFormat12).toFormatter());
            dateTimeFormats.add(dateTimeBuilder(dateFormat, timeFormat24).toFormatter());

            // Note that the auto-detected format should still apply. This is here to detect an
            // annoying difference between 'Sep' and 'Sept' that arises when running JDK 17
            // https://stackoverflow.com/questions/69267710/septembers-short-form-sep-no-longer-parses-in-java-17-in-en-gb-locale
            dateTimeFormats.add(dateTimeBuilder(dateFormat, timeFormat12).toFormatter(Locale.US));
            dateTimeFormats.add(dateTimeBuilder(dateFormat, timeFormat24).toFormatter(Locale.US));
            dateTimeFormats.add(dateTimeBuilder(dateFormat, timeFormat12).toFormatter(Locale.UK));
            dateTimeFormats.add(dateTimeBuilder(dateFormat, timeFormat24).toFormatter(Locale.UK));
        }
    }

    private static DateTimeFormatterBuilder dateTimeBuilder(String dateFormat, String timeFormat) {
        return new DateTimeFormatterBuilder()
                .parseLenient()
                .parseCaseInsensitive()
                .appendPattern(dateFormat)
                .appendPattern(timeFormat);
    }

    public boolean isDateFormatSet() {
        return !dateTimeFormats.isEmpty();
    }

    public boolean hasConnected() {
        return hasConnected;
    }

    /**
     * @return null if no date-format is known, or if no (parseable) times were found.
     */
    public String getLastModifiedTicketTimeIfKnown() {
        return lastModifiedTicketTime;
    }

    public LocalDateTime parseDateTime(String str) throws DateTimeParseException {
        DateTimeParseException exception = null;
        for (DateTimeFormatter format : dateTimeFormats) {
            try {
                return format.parse(str, LocalDateTime::from);
            } catch (DateTimeParseException ex) {
                if (exception == null) {
                    exception = ex;
                }
            }
        }
        if (exception == null) {
            throw new AssertionError();
        }
        throw exception;
    }

    public void setLastModifiedTicketTime(List<String> dateTimeStrings) {
        hasConnected = true;
        if (dateTimeFormats.isEmpty()) {
            return;
        }

        LocalDateTime latest = null;
        String latestString = null;
        for (String changeTime : dateTimeStrings) {
            if (changeTime != null) {
                try {
                    LocalDateTime date = parseDateTime(changeTime);
                    if (latest == null || date.isAfter(latest)) {
                        latest = date;
                        latestString = changeTime;
                    }
                } catch (DateTimeParseException e) {
                    System.err.println("Date format not parseable: " + changeTime);
                }
            }
        }

        // HACK: not checking if greater, because in current usage it always will be
        if (latestString != null) {
            System.out.println("Last modified update: " + lastModifiedTicketTime + " -> " + latestString);
            lastModifiedTicketTime = latestString;
        }
    }

    public static void main(String[] args) {
        TracInstantProperties.initialise("bettyluke.net", "TracInstantTests");

        SiteData site = new SiteData();
        site.setDateFormat("MMM dd, yyyy");

        // Yeah, yeah, it's all yearning for unit tests. Problem with this particular project is
        // I'm always just dipping in & in too much of a hurry to go speed up my life like that!
        // TODO: Will do "Soon" ;-)
        String[] dateStrings = new String[] {
                "Jan 11, 2017, 1:30:37 PM",
                "Jan 11, 2017",
                "Jan 11, 2017 ",
                "Jan 11, 2017 12",     // Invalid
                "Jan 11, 2017 12:",    // Invalid
                "Jan 11, 2017 12:58",
                "Jan 11, 2017 12:58:", // Invalid
                "Jan 11, 2017 12:58:38",
                "Jan 11, 2017 12:58:38 ",
                "Jan 11, 2017 2:58",
                "Jan 11, 2017 2:58:",  // Invalid
                "Jan 11, 2017 2:58:38",
                "Jan 11, 2017 20:58:38 ",
                "Jan 11, 2017 12:58:38 PM",
                "Jan 1, 2017, ",
                "Jan 1, 2017, 12",     // Invalid
                "Jan 1, 2017, 12:",    // Invalid
                "1 1, 2017, 12:58",
                "Jan 1, 17, 12:58:",   // Invalid
                "Jan 1, 17, 12:58:38",
                "Jan 1, 17, 12:58:38 ",
                "Jan 1, 17, 12:58:38 PM",
                "Jan 1, 17, 1:8:",     // Invalid
                "Jan 1, 17, 1:8:8",
                "Jan 1, 17, 20:08:08",
                "Jan 1, 17, 20:8:8 PM",
                "Jan 1, 17, 20:8:8 AM" // Invalid
        };

        for (String dateString : dateStrings) {
            try {
                System.out.print("date: " + dateString);
                site.dateTimeFormats.get(0).parse(dateString);
                System.out.println(" parsed");
                continue;
            } catch (Exception e) {
                System.out.println(" FAILED");
            }
        }
    }
}
