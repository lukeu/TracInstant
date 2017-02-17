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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.bettyluke.tracinstant.prefs.SiteSettings;
import net.bettyluke.util.XML10FilterReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class SlurpTask extends TicketLoadTask {

    private static final String STATUS_EXCLUSION_PLACEHOLDER = "<<STATUS>>";

    private static final String FIELDS_QUERY =
        "query?format=tab" + STATUS_EXCLUSION_PLACEHOLDER +
        "&col=id&col=summary&col=cc&col=status&col=type" +
        "&col=keywords&col=reporter&col=component&col=priority" +
        "&col=owner&col=milestone&col=severity" +
        "&col=resolution&col=version&order=id";

    private static final int RESULTS_PER_PAGE = 200;

    // A query to slurp pages while still supporting Trac 0.10, which did not support
    // the 'max' and 'page' requests (and so slurps everything at once).
    private static final String RSS_QUERY =
        "query?format=rss" +
        STATUS_EXCLUSION_PLACEHOLDER +
        "&order=id" +
        "&max=" + RESULTS_PER_PAGE;


    // Note: ordering by changetime is required by the heuristics in DateFormatDetector
    private static final String MODIFIED_TIME_QUERY =
        "query?format=tab" + STATUS_EXCLUSION_PLACEHOLDER +
        "&col=id&col=changetime&order=changetime";

    private final SiteSettings siteSettings;

    /** Nullable: null means fetch-all */
    private final String sinceDateTime;

    /** An externally-executed scan of the attachmentFolder, we monitor its completion. */
    private final Future<?> attachmentScanFuture;

    /**
     * The format used only to form part of url requests. Trac appears to support this
     * format irrespective of user/server date settings.
     */
    DateTimeFormatter urlDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Exception fault = null;

    public SlurpTask(SiteData site, SiteSettings siteSettings, String since, Future<?> attachmentScanFuture) {
        super(site);
        this.siteSettings = siteSettings;
        this.sinceDateTime = since;
        this.attachmentScanFuture = attachmentScanFuture;
    }

    public boolean isIncremental() {
        return sinceDateTime != null;
    }

    public Exception getFault() {
        return fault;
    }

    @Override
    protected List<String> doInBackground() throws Exception {
        try {
            return doInternal();
        } catch (Exception e) {
            fault = e;
            throw e;
        }
    }

    protected List<String> doInternal() throws IOException, SAXException, InterruptedException {

        // Slurp timestamps prior to all other data.
        TicketProvider changetimeProvider = slurpChangetimes();
        List<Ticket> tickets = changetimeProvider.getTickets();
        List<String> dateTimeStrings = extractModificationDates(tickets);

        // Update DateFormat always when (and only when) doing a full slurp
        if (sinceDateTime == null) {
            updateDateFormat(dateTimeStrings);
        }

        if (isTicketModified(tickets)) {
            System.out.println("" + tickets.size() + " tickets require field updates");

            slurpFields(FIELDS_QUERY);
            slurpDescriptions(tickets.size());

            // Finally publish timestamps AFTER slurping all other data.
            publish(new Update(changetimeProvider));
        }

        // Monitor the completion of attachment folder scanning. (It is hacked in here
        // so that status updates are more-simple: they are issued from only one source.)
        if (!siteSettings.getAttachmentsDir().trim().isEmpty()) {
            publish(new Update("Scanning Attachments Folder... ",
                    "Scanning: " + siteSettings.getAttachmentsDir()));
            awaitCompletionNoExceptions(attachmentScanFuture, 10, TimeUnit.SECONDS);
        }

        // All data for external consumption has been passed out via the publish/process mechanism.
        // Here we return just the timestamps to update the 'last-modified' record in SiteData.
        return dateTimeStrings;
    }

    private void updateDateFormat(List<String> dateTimeStrings) {
        String format = DateFormatDetector.detectFormat(dateTimeStrings);
        if (format != null) {
            System.out.println("Auto-detected date format: " + format);
        }
        site.setDateFormat(format);
    }

    private boolean isTicketModified(Collection<Ticket> tickets) {
        String mostRecentlyModifiedTime = site.getLastModifiedTicketTimeIfKnown();
        if (mostRecentlyModifiedTime == null) {
            return true;
        }
        return streamChangeTimes(tickets).anyMatch(ct -> !ct.equals(mostRecentlyModifiedTime));
    }

    private TicketProvider slurpChangetimes() throws IOException, InterruptedException {
        URL url = new URL(makeQueryURL(MODIFIED_TIME_QUERY));
        publish(new Update("Checking ticket timestamps...", "Querying: " + url));
        return slurpTabDelimited(url);
    }

    private int slurpFields(String query) throws IOException, InterruptedException {
        URL url = new URL(makeQueryURL(query));
        publish(new Update("Downloading ticket fields...", "Querying: " + url));
        TicketProvider tabData = slurpTabDelimited(url);
        publish(new Update(tabData));
        return tabData.getTickets().size();
    }

    private String makeModifiedFilter() {
        if (sinceDateTime != null && site.isDateFormatSet()) {
            try {
                String reformatted =
                        urlDateFormat.format(
                                site.parseDateTime(sinceDateTime));
                return "&changetime=" + URLEncoder.encode(reformatted, "UTF-8") + "..";
            } catch (UnsupportedEncodingException | DateTimeParseException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    /**
     * Attempts to slurp descriptions a page at a time, with fall-back support for
     * Trac 0.10, whereby we must slurp all descriptions in one go.
     */
    private void slurpDescriptions(int expectedCount) throws IOException, SAXException {
        String basicDescriptionURL = makeQueryURL(RSS_QUERY);
        String pageSuffix = "";

        int found = 0;
        int page = 1;
        while (found < expectedCount) {
            URL url = new URL(basicDescriptionURL + pageSuffix);
            publish(new Update("Downloading ticket descriptions (" +
            (found*100/expectedCount) + "%)...", "Querying: " + url));
            int foundNew = slurpXmlFormat(url);
            found += foundNew;
            if (found < expectedCount && foundNew < RESULTS_PER_PAGE) {
                System.err.println("Number of results found");
                break;
            }
            pageSuffix = "&page=" + (++page);
        }
    }

    public static void awaitCompletionNoExceptions(
            Future<?> future, long timeout, TimeUnit unit) {
        try {
            future.get(timeout, unit);
        } catch (CancellationException e) {
            // Ignore
        } catch (InterruptedException e) {

            future.cancel(true);

            // Don't mask the interrupt state from higher-up the call stack.
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    private String makeQueryURL(String queryFormat) {
        return siteSettings.getURL() + '/' + queryFormat.replaceAll(
            STATUS_EXCLUSION_PLACEHOLDER,
            siteSettings.isFetchOnlyActiveTickets() ? "&status=!closed" : "") +
            makeModifiedFilter();
    }

    private InputStream authenticateAndGetStream(URL url) throws IOException {
        return AuthenticatedHttpRequester.getInputStream(siteSettings, url);
    }

    private int slurpXmlFormat(URL url) throws IOException, SAXException {
        try (InputStream in = authenticateAndGetStream(url)) {

            // Parse, filtering-out duff chars. Note one proposal of converting the
            // header to the more lenient XML 1.1 <?xml version="1.1"?> still failed
            // to handle some crap spewed out by one test server.
            TicketProvider xmlData = TracXmlTicketParser.parse(
                new InputSource(new XML10FilterReader(new InputStreamReader(
                    new BufferedInputStream(in), "UTF-8"))));
            publish(new Update(xmlData));
            int count = xmlData.getTickets().size();
            xmlData = null;
            return count;
        }
    }

    private TicketProvider slurpTabDelimited(URL url)
            throws MalformedURLException, IOException, InterruptedException {
        try (InputStream in = authenticateAndGetStream(url)) {
            return TracTabTicketParser.parse(
                new InputStreamReader(new BufferedInputStream(in), "UTF-8"));
        }
    }
}
