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

package net.bettyluke.tracinstant.ui;

import java.awt.event.ActionEvent;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.swing.AbstractAction;

import net.bettyluke.tracinstant.data.SiteData;
import net.bettyluke.tracinstant.data.SlurpTask;
import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.tracinstant.download.AttachmentCounter;
import net.bettyluke.tracinstant.prefs.SiteSettings;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;

/** 
 * HACK: Currently the SlurpAction is in charge of cancelling the task when a new
 * 'slurp' request is made. This places the restriction that there can only be one
 * SlurpAction created.
 * <p> 
 * TODO: Make a class like 'SiteLoader' that manages the loading and cancelling of all 
 * state: cached-data, remote-data, attachments folder, user-fields       
 */
public class SlurpAction extends AbstractAction {

    private final TracInstantFrame frame;
    private final SiteData site;

    /** The slurp task, held so it can be cancelled. */
    private SlurpTask task = null;
    
    private final Runnable onTaskEnded = new Runnable() {
        public void run() {
            SlurpTask t = task;
            task = null;
            try {
                t.get();
            } catch (CancellationException e) {
                // Ignore
            } catch (InterruptedException e) {
                // Ignore
            } catch (ExecutionException e) {
                promptToRetry(t);
            }
        }

        private void promptToRetry(SlurpTask t) {
            if (t.isIncremental()) {
                promptAndSlurpIncremental();
            } else {
                promptAndSlurpAll();
            }
        }
    };

    public SlurpAction(TracInstantFrame frame, SiteData site) {
        super("Connect to...");
        this.frame = frame;
        this.site = site;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        promptAndSlurpAll();
    }

    /** @return Whether slurping was initiated. */
    public boolean promptAndSlurpAll() {
        if (promptForTracSettings()) {
            site.reset();
            slurpAll();
            site.loadUserData();
            return true;
        }
        return false;
    }

    /** @return Whether slurping was initiated. */
    public boolean promptAndSlurpIncremental() {
        if (promptForTracSettings()) {
            slurpIncremental();
            site.loadUserData();
            return true;
        }
        return false;
    }

    /** @return Whether slurping was initiated. */
    public boolean promptIfNecessaryAndSlurpIncremental() {
        if (!isNecessaryToPrompt() || promptForTracSettings()) {
            slurpIncremental();
            site.loadUserData();
            return true;
        }
        return false;
    }

    private boolean isNecessaryToPrompt() {
        if (task != null) {
            return false;
        }
        SiteSettings settings = SiteSettings.getInstance();
        if (settings.getURL().isEmpty()) {
            return true;
        }
        if (!settings.getUsername().isEmpty() && settings.getPassword().isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean promptForTracSettings() {
        SiteSettings settings = SiteSettings.getInstance();
        TracUrlSelectionPanel panel = new TracUrlSelectionPanel(settings);
        panel.setURLHistory(TracInstantProperties.getURL_MRU());
        panel.setAttachmentsDirHistory(TracInstantProperties.getAttachmentsDir_MRU());
        
        settings = panel.showAsDialog(frame);
        if (settings != null) {
            settings.updatePreferences();
            return true;
        }
        
        // Dialog cancelled
        return false;
    }

    private void slurpAll() {
        cancel();
        slurp(null);
    }
    
    private void slurpIncremental() {
        if (task != null) {
            
            // TODO: Should we instead queue up a single incremental update(?)
            System.out.println("Incremental update aborted due to running tasks");
            return;
        }
        if (site.getDateFormat() == null) {
            System.err.println("Incremental update disabled: unknown server DateFormat");
            slurpAll();
            return;
        }
        Ticket[] tickets = site.getTableModel().getTickets();
        String lastChanged = SlurpTask.getMostRecentlyModifiedTime(site, tickets);
        System.out.println("Last changed ticket:" + lastChanged);
        
        slurp(lastChanged);
    }

    /**
     * Terminate any in-progress slurp task. It may carry on for a while - but 
     * it will discard its results. (No need to wait for it.)
     */
    public void cancel() {
        if (task != null) {
            task.cancel(true);
        }
    }

    private void slurp(String lastChanged) {
        SiteSettings settings = SiteSettings.getInstance();

        // Fork the attachment folder scan...
        Future<?> attachmentScanFuture = 
            AttachmentCounter.scanAttachmentsFolderAsynchronously(
                settings.getAttachmentsDir());
        task = new SlurpTask(site, settings, lastChanged, attachmentScanFuture);
        frame.monitorTask(task);
        task.executeWithNotification(onTaskEnded);
    }
}
