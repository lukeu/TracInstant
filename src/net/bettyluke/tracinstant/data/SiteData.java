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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import net.bettyluke.tracinstant.prefs.TracInstantProperties;

public class SiteData {

    static final String TABULAR_CACHE_FILE = "SiteCache_Tabular.txt";
    static final String HIDDEN_FIELDS_CACHE_FILE = "SiteCache_Hidden.txt";

    private final TicketTableModel m_TableModel = new TicketTableModel();
    private String dateTimeFormatString = null;
    private DateTimeFormatter dateTimeFormat12 = null;
    private DateTimeFormatter dateTimeFormat24 = null;
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
                && new File(getAppDataDir(), TABULAR_CACHE_FILE).canRead()
                && new File(getAppDataDir(), HIDDEN_FIELDS_CACHE_FILE).canRead();
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
            File file = getAppFileForReading(fileName);
            if (file != null) {
                try (FileReader reader = new FileReader(file)) {
                    return TracTabTicketParser.parse(reader);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static File getAppFileForReading(String simpleFileName) throws IOException {
        File dataDir = getAppDataDir();
        if (!dataDir.isDirectory()) {
            return null;
        }
        File file = new File(dataDir, simpleFileName);
        if (!file.exists()) {
            return null;
        }
        return file;
    }

    private static File getAppDataDir() throws IOException {
        return TracInstantProperties.get().getAppDataDirectory();
    }

    private void saveTicketData(String fileName, Set<String> fields) {
        try {
            File dataDir = getAppDataDir();
            if (!dataDir.isDirectory() && !dataDir.mkdirs()) {
                throw new IOException("Directory could not be created: " + dataDir);
            }
            File dataFile = new File(dataDir, fileName);
            List<Ticket> tickets = m_TableModel.getTicketsWithAnyField(fields);
            TabTicketWriter.write(new FileWriter(dataFile), fields, tickets);
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
            File file = new File(getAppDataDir(), name);
            file.delete();
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
        dateTimeFormat12 = dateFormat == null ? null :
            newDateTimeFormatter(dateFormat, "[','][';'][' '][['t']h:m[:s][ ]a]");
        dateTimeFormat24 = dateFormat == null ? null :
            newDateTimeFormatter(dateFormat, "[','][';'][' '][['t']HH:mm[:ss]]");
    }

    private static DateTimeFormatter newDateTimeFormatter(String dateFormat, String timeFormat) {
        return new DateTimeFormatterBuilder()
                .parseLenient()
                .parseCaseInsensitive()
                .appendPattern(dateFormat)
                .appendPattern(timeFormat)
                .toFormatter();
    }

    public boolean isDateFormatSet() {
        return dateTimeFormat12 != null;
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
        try {
            return dateTimeFormat12.parse(str, LocalDateTime::from);
        } catch (DateTimeParseException ex) {
        }
        return dateTimeFormat24.parse(str, LocalDateTime::from);
    }

    public void setLastModifiedTicketTime(List<String> dateTimeStrings) {
        hasConnected = true;
        if (!isDateFormatSet()) {
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
                site.dateTimeFormat12.parse(dateString);
                System.out.println(" parsed");
                continue;
            } catch (Exception e) {
                System.out.println(" FAILED");
            }
        }
    }
}
