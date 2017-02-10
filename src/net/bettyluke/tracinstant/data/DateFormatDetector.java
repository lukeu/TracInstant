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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateFormatDetector {

    private static final int MAX_SAMPLES_TO_CONSIDER = 60;

    private static final List<String> PERMUTATIONS = Arrays.asList(
        "yyyy-MM-dd",
        "yyyy-MMM-dd",
        "yyyy-MMMMM-dd",
        "dd-MM-yyyy",
        "dd-MMM-yyyy",
        "dd-MMMMM-yyyy",
        "dd-MM-yy",
        "dd-MMM-yy",
        "dd-MMMMM-yy",
        "MM-dd-yyyy",
        "MMM-dd-yyyy",
        "MMMMM-dd-yyyy",
        "MM-dd-yy",
        "MMM-dd-yy",
        "MMMMM-dd-yy",
        "yy-MM-dd",
        "yy-MMM-dd",
        "yy-MMMMM-dd",

        // Text months with comma
        "MMM-dd,-yyyy",
        "MMMMM-dd,-yyyy",
        "MMM-dd,-yy",
        "MMMMM-dd,-yy",

        // Text months Including days
        "EEE, dd-MMM-yyyy",
        "EEE, dd-MMMMM-yyyy",
        "EEEE, dd-MMM-yyyy",
        "EEEE, dd-MMMMM-yyyy"
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
        "[\\,\\;]?[ tT]*\\d{1,2}:\\d{1,2}(:\\d{1,2})?");


    // TODO: Switch DateFormat -> DateTimeFormatter for consistency
    private static class Attempt {
        private static final Date LONG_AGO =
            new Date(-TimeUnit.MILLISECONDS.convert(365 * 200, TimeUnit.DAYS));

        public Attempt(String dateString) {
            string = dateString;
            format = new SimpleDateFormat(dateString);
            format.setLenient(false);
            latestMatchedDate = LONG_AGO;
        }

        public String string;
        public DateFormat format;
        public Date latestMatchedDate;
    }

    private final List<String> dateStringsAscending;
    private final List<Attempt> possibleFormats;

    /**
     * Will decrement with each check after only a single 'possibleFormat' remains.
     * When it reaches zero we will terminate with success.
     */
    private int confirmationsRemaining = 10;

    public static String detectFormat(List<String> dateTimeStringsAscending) {
        return new DateFormatDetector(dateTimeStringsAscending).detectFormat();
    }

    private DateFormatDetector(List<String> dateTimeStringsAscending) {
        this.dateStringsAscending = stripTime(trimIfLarge(dateTimeStringsAscending));

        possibleFormats = new ArrayList<>(PERMUTATIONS.size() * 4);
        for (String dateString : PERMUTATIONS) {
            possibleFormats.add(new Attempt(dateString));
            possibleFormats.add(new Attempt(dateString.replaceAll("\\-", "/")));
            possibleFormats.add(new Attempt(dateString.replaceAll("\\-", ".")));
            possibleFormats.add(new Attempt(dateString.replaceAll("\\-", " ")));
        }
    }

    private List<String> trimIfLarge(List<String> ss) {
        if (ss.size() <= MAX_SAMPLES_TO_CONSIDER) {
            return ss;
        }

        // Use the start and end of the given list.
        List<String> result = new ArrayList<>(MAX_SAMPLES_TO_CONSIDER);
        for (int i = 0; i < MAX_SAMPLES_TO_CONSIDER / 2; i++) {
            result.add(ss.get(i));
        }
        for (int i = ss.size() - MAX_SAMPLES_TO_CONSIDER / 2; i < ss.size(); i++) {
            result.add(ss.get(i));
        }
        return result;
    }

    private static List<String> stripTime(List<String> dateTimesAscending) {
        List<String> result = new ArrayList<>(dateTimesAscending.size());
        for (String s : dateTimesAscending) {
            result.add(stripTime(s));
        }
        return result;
    }

    private static String stripTime(String dateTime) {
        dateTime = dateTime.trim();
        Matcher m = TIME_PATTERN.matcher(dateTime);
        if (m.find()) {
            return dateTime.substring(0, m.start()).trim();
        }
        return dateTime;
    }

    private String detectFormat() {
        for (String dateString : dateStringsAscending) {
            eliminateFormats(dateString);
        }

        for (String dateString : dateStringsAscending) {
            eliminateIncorrectFieldLengths(dateString);
        }

        if (possibleFormats.size() > 1) {
            System.err.println("WARNING ambiguous date format. Possibilites:");
            for (Attempt a : possibleFormats) {
                System.err.println("    " + a.string);
            }
        }
        return possibleFormats.isEmpty() ? null : possibleFormats.get(0).string;
    }

    /**
     * This heuristic uses two tests:
     *  1) the non-lenient DateFormat must successfully parse the date, and
     *  2) the parsed date must be greater than the previous date parsed by the same
     *     DateFormat instance.
     * Any DateFormat not meeting these tests is removed.
     */
    private void eliminateFormats(String dateString) {
        int i = 0;
        while (i < possibleFormats.size()) {
            Attempt attempt = possibleFormats.get(i);
            DateFormat format = attempt.format;
            Date latestDate = attempt.latestMatchedDate;
            try {
                Date date = format.parse(dateString);
                if (date.compareTo(latestDate) >= 0) {
                    attempt.latestMatchedDate = date;
                    i++;
                    continue;
                }
            } catch (ParseException e) {
            }
            possibleFormats.remove(i);
        }
    }

    /**
     * Sample date strings with 4-digit years will be accepted by formats using either
     * 'yyyy' or 'yy'; similarly and "June" will match both MMM and MMMMM formats.
     * <p>
     * This is heuristic that parse and reformats a given date-string, and compares the
     * result with the original. Spaces and leading-zeros are dropped for the comparison.
     */
    private void eliminateIncorrectFieldLengths(String dateString) {
        Iterator<Attempt> it = possibleFormats.iterator();
        while(it.hasNext()) {
            Attempt a = it.next();
            try {
                String reformatted = a.format.format(a.format.parse(dateString));
                if (stripZerosSpaces(dateString).equals(stripZerosSpaces(reformatted))) {

                    // Early termination
                    if (possibleFormats.size() == 1) {
                        if ((--confirmationsRemaining) == 0) {
                            return;
                        }
                    }
                    continue;
                }
            } catch (ParseException e) {
            }
            it.remove();
        }
    }

    private String stripZerosSpaces(String dateString) {
        return dateString.replaceAll("\\b0", "").replaceAll("\\s+", "");
    }

    public static void main(String[] args) {
        final String[][] TESTS = new String[][] {
            {
                "Sep 5, 2002, 11:41:15 AM",
                "Sep 5, 2002; 11:41:15 AM"
            },
            {
                "03/12/03 10:01:16",
                "22/04/04 11:20:38",
                "21/09/04 13:22:50"
            },
            {
                "2003-08-24 22:51:08"
            },
            {
                "2002 2 22"
            },
            {
                "2002. 02. 2"
            },
            {
                "Sep 5, 2002"
            },
            {
                "November 5, 2002"
            },
            {
                "5 August 99"
            },
            {
                "Fri, 26 Oct 2007 12:56:12 GMT",
                "Fri, 16 Nov 2007 22:40:34 GMT"
            },
            {
                "Friday, 26 Oct 2007 12:56:12 GMT"
            },
            {
                "2012-04-10T09:37:31+01:00",
            }
        };
        for (String[] test : TESTS) {
            String detected = detectFormat(Arrays.asList(test));
            if (detected == null) {
                System.err.println("No matching format for: " + Arrays.toString(test));
            } else {
                System.out.println(detected + " -> " +
                    new SimpleDateFormat(detected).format(new Date()));
            }
        }
    }
}
