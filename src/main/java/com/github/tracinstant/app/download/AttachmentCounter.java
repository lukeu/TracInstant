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

package com.github.tracinstant.app.download;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.github.tracinstant.app.data.AuthenticatedHttpRequester;
import com.github.tracinstant.app.data.Ticket;
import com.github.tracinstant.app.download.Downloadable.FileDownloadable;
import com.github.tracinstant.app.download.Downloadable.TracDownloadable;
import com.github.tracinstant.app.prefs.SiteSettings;
import com.github.tracinstant.app.prefs.TracInstantProperties;

public class AttachmentCounter {

    private static final Pattern NAME_MATCHER = Pattern.compile("^(\\d+).*");
    private static final int MAX_SEARCH_DEPTH = 8;
    private static final BiPredicate<Path, BasicFileAttributes> IS_FILE_PREDICATE =
            (p, attrs) -> !attrs.isDirectory() && !attrs.isSymbolicLink();

    private static final Pattern ATTACHMENT_LINK =
            Pattern.compile("href=\\\"(/attachment/ticket/[^\\\"]+)\\\"");
    /**
     * All matching directories found under the top attachment directory. This is cached after each
     * incremental slurp to reduce a bit of work during UI events, like table selection changes.
     * (i.e. don't bother searching in directories that don't exist.)
     */
    private static Map<Integer, Path> s_AttachmentSubDirs =
            Collections.synchronizedMap(new TreeMap<Integer, Path>());

    /**
     * The most recently executed AttachmentCounter. Thread-safety: always accessed
     * by the EDT.
     */
    private static AttachmentCounter s_CurrentJob = null;

    /** Counts attachments found attached to ticket(s). */
    private final TracCounter m_TicketCounter = new TracCounter();

    /** Counts attachments found under the additional attachment directory. */
    private final FileCounter m_DirectoryCounter = new FileCounter();

    private final Ticket[] m_Tickets;
    private final CountCallback m_Callback;

    private boolean m_FoundFiles = false;
    private boolean m_FoundAttachments = false;
    private boolean m_Cancelled = false;

    private AttachmentCounter(Ticket[] tickets, CountCallback callback) {
        m_Tickets = tickets;
        m_Callback = callback;
    }

    public static interface CountCallback {
        void restart();

        void downloadsFound(List<? extends Downloadable> attachments);

        void done();
    }

    /**
     * Asynchronously scan the attachments folder for sub-directories matching
     * {@link #NAME_MATCHER}. Downloads will not be available until this scanning is
     * performed and is complete.
     *
     * @return An object that can be used to determine the success of scanning, or
     * cancel the background task.
     */
    public static Future<Map<Integer, Path>> scanAttachmentsFolderAsynchronously(String topFolder) {

        SwingWorker<Map<Integer, Path>, Void> scanner = new SwingWorker<Map<Integer,Path>, Void>() {

            @Override
            protected Map<Integer, Path> doInBackground() throws Exception {
                return scanAttachmentsFolder();
            }

            private Map<Integer, Path> scanAttachmentsFolder() {
                long t0 = System.nanoTime();

                if (topFolder.trim().isEmpty()) {
                    return Collections.emptyMap();
                }

                Map<Integer, Path> results =
                        Collections.synchronizedMap(new TreeMap<Integer, Path>());

                Path bugDir = Paths.get(topFolder);
                assert bugDir.isAbsolute();

                // Unfortunately there doesn't seem to be an easy way to interrupt this
                // potentially-long command. (It is slow on certain network drives.)
                String[] listing = bugDir.toFile().list();

                if (listing == null) {
                    System.err.println("Failed to list directory: " + bugDir);
                    return results;
                }

                for (String name : listing) {
                    Matcher m = NAME_MATCHER.matcher(name);
                    if (m.matches()) {
                        String id = m.group(1);
                        Path subDir = bugDir.resolve(name);
                        try {
                            results.put(Integer.valueOf(id), subDir);
                        } catch (NumberFormatException ex) {
                            // Ignore. Perhaps a 'big number'.
                        }
                    }
                }

                long t1 = System.nanoTime();
                System.out.format("Time to scan the AttachmentsFolder: %.2f ms\n",
                        (t1 - t0) / 1000000f);
                return results;
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    // Not exactly atomic... oh well.
                    s_AttachmentSubDirs.clear();
                    s_AttachmentSubDirs.putAll(get());
                } catch (InterruptedException | ExecutionException e) {
                }
            }
        };

        scanner.execute();
        return scanner;
    }

    public static void restartCounting(Ticket[] tickets, CountCallback callback) {
        assert SwingUtilities.isEventDispatchThread();
        if (s_CurrentJob != null) {
            s_CurrentJob.cancel();
        }
        callback.restart();
        s_CurrentJob = new AttachmentCounter(tickets, callback);
        s_CurrentJob.m_DirectoryCounter.execute();
        s_CurrentJob.m_TicketCounter.execute();
    }

    private void cancel() {
        m_Cancelled = true;
        m_DirectoryCounter.cancel(true);
        m_TicketCounter.cancel(true);
    }

    /** Counts attachments found under the additional attachment directory. */
    public class FileCounter extends SwingWorker<Void, FileDownloadable> {

        @Override
        protected Void doInBackground() throws InterruptedException {
            for (Ticket ticket : m_Tickets) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                Path ticketDir = s_AttachmentSubDirs.get(ticket.getNumber());
                if (ticketDir == null) {
                    continue;
                }
                try (Stream<Path> paths = streamRelativeFiles(ticketDir)) {
                    FileDownloadable[] downloadable = paths
                            .map(p -> new FileDownloadable(ticket.getNumber(), ticketDir, p))
                            .toArray(FileDownloadable[]::new);
                    publish(downloadable);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        private Stream<Path> streamRelativeFiles(Path bugDir) throws IOException {

            // No idea why attribs.isRegularFile() returns false on Windows Network (UNC) paths...
            return Files
                    .find(bugDir, MAX_SEARCH_DEPTH, IS_FILE_PREDICATE)
                    .map(p -> bugDir.relativize(p));
        }

        @Override
        protected void process(List<FileDownloadable> chunks) {
            if (!m_Cancelled) {
                m_Callback.downloadsFound(chunks);
            }
        }

        @Override
        protected void done() {
            m_FoundFiles = true;
            checkAllDone();
        }
    }

    /** Counts attachments found attached to Trac ticket(s). */
    public class TracCounter extends SwingWorker<Void, TracDownloadable> {

        @Override
        protected Void doInBackground() throws InterruptedException {
            for (Ticket ticket : m_Tickets) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                try {
                    int ticketNum = ticket.getNumber();
                    URL listingURL = createAttachmentPageURL(ticketNum);
                    scanTracAttachementPage(ticketNum, listingURL);
                } catch (MalformedURLException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {

                    // TODO: Error reporting
                    ex.printStackTrace();
                }
            }
            return null;
        }

        private URL createAttachmentPageURL(int number) throws MalformedURLException {

            // Trailing '/' required for Trac 0.12 (wasn't needed for 0.10)
            return new URL(TracInstantProperties.getURL() + "/attachment/ticket/" + number + '/');
        }

        private void scanTracAttachementPage(int ticketNum, URL url) throws IOException {
            try (InputStream in = getAuthenticatedInputStream(url);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = ATTACHMENT_LINK.matcher(line);
                    if (m.find()) {
                        publish(new TracDownloadable(ticketNum, m.group(1), 0));
                    }
                }
            }
        }

        private InputStream getAuthenticatedInputStream(URL url) throws IOException {
            return AuthenticatedHttpRequester.getInputStream(SiteSettings.getInstance(), url);
        }

        @Override
        protected void process(List<TracDownloadable> chunks) {
            if (!m_Cancelled) {
                m_Callback.downloadsFound(chunks);
            }
        }

        @Override
        protected void done() {
            m_FoundAttachments = true;
            checkAllDone();
        }
    }

    void checkAllDone() {
        assert SwingUtilities.isEventDispatchThread();
        if (!m_Cancelled && m_FoundFiles && m_FoundAttachments) {
            m_Callback.done();
        }
    }
}
