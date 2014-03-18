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

package net.bettyluke.tracinstant.download;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import net.bettyluke.tracinstant.data.AuthenticatedHttpRequester;
import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.tracinstant.download.Downloadable.FileDownloadable;
import net.bettyluke.tracinstant.download.Downloadable.TracDownloadable;
import net.bettyluke.tracinstant.prefs.SiteSettings;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;
import net.bettyluke.util.FileUtils;


public class AttachmentCounter {
    
    private static final Pattern NAME_MATCHER = Pattern.compile("^(\\d+).*");
    
    private static final Pattern ATTACHMENT_LINK =
        Pattern.compile("href=\\\"(/attachment/ticket/[^\\\"]+)\\\"");

    /** A master list, collected once, of all AttachmentDirectory sub-directories. */
    private static Map<Integer,File> s_AttachmentDirListing =
        Collections.synchronizedMap(new TreeMap<Integer,File>());
    
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
    public static Future<Map<Integer,File>> scanAttachmentsFolderAsynchronously(
            final String topFolder) {
        
        SwingWorker<Map<Integer, File>, Void> scanner =
                new SwingWorker<Map<Integer,File>, Void>() {
            
            @Override
            protected Map<Integer, File> doInBackground() throws Exception {
                return scanAttachmentsFolder();
            }
            
            private Map<Integer, File> scanAttachmentsFolder() {
                long t0 = System.nanoTime();
                
                if (topFolder.trim().isEmpty()) {
                    return Collections.emptyMap();
                }
                
                Map<Integer,File> results =
                    Collections.synchronizedMap(new TreeMap<Integer,File>());
                
                File bugDir = new File(topFolder);

                // Unfortunately there doesn't seem to be an easy way to interrupt this
                // potentially-long command. (It is slow on certain network drives.) 
                String[] listing = bugDir.list();
                
                if (listing == null) {
                    System.err.println("Failed to list directory: " + bugDir);
                    return results;
                }
                
                for (String name : listing) {
                    Matcher m = NAME_MATCHER.matcher(name);
                    if (m.matches()) {
                        String id = m.group(1);
                        File subDir = new File(bugDir, name);
                        results.put(Integer.valueOf(id), subDir);
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
                    s_AttachmentDirListing.clear();
                    s_AttachmentDirListing.putAll(get());
                } catch (InterruptedException e) {
                } catch (ExecutionException e) {
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

            // Until Java 7 comes out, I'm going to turn a blind eye to trying to
            // enumerate or copy deeply - only files directly in the folder sorry.
            for (Ticket ticket : m_Tickets) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                File bugDir = s_AttachmentDirListing.get(ticket.getNumber());
                if (bugDir == null) {
                    continue;
                }
                File[] files = bugDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            publish(new FileDownloadable(ticket.getNumber(), f));
                        }
                    }
                }
            }
            return null;
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
            return new URL(
                TracInstantProperties.getURL() + "/attachment/ticket/" + number + '/');
        }
        
        private void scanTracAttachementPage(int ticketNum, URL url) throws IOException {
            InputStream in = null;
            BufferedReader reader = null;
            try {
                in = AuthenticatedHttpRequester.openStreamBlindlyTrustingAnySslCertificates(
                        SiteSettings.getInstance(), url);
                reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = ATTACHMENT_LINK.matcher(line);
                    if (m.find()) {
                        publish(new TracDownloadable(ticketNum, m.group(1), 0));
                    }
                }
            } finally {
                FileUtils.close(in);
                FileUtils.close(reader);
            }
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
