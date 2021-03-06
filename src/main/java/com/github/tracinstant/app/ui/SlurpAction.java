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

package com.github.tracinstant.app.ui;

import java.awt.event.ActionEvent;
import java.util.concurrent.Future;

import javax.swing.AbstractAction;

import com.github.tracinstant.app.data.SiteData;
import com.github.tracinstant.app.data.SlurpTask;
import com.github.tracinstant.app.download.AttachmentCounter;
import com.github.tracinstant.app.prefs.SiteSettings;
import com.github.tracinstant.app.prefs.TracInstantProperties;

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

    private final Runnable taskClearer = () -> { task = null; };

    public SlurpAction(TracInstantFrame frame, SiteData site) {
        super("Connect to...");
        this.frame = frame;
        this.site = site;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (promptForTracSettings()) {
            resetAndSlurpAll();
        }
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

    private void resetAndSlurpAll() {
        site.reset();
        slurpAll();
    }

    public void slurpAll() {
        cancel();
        site.loadUserData();
        slurp(null);
    }

    public String slurpIncremental() {
        if (task != null) {

            // TODO: Should we instead queue up a single incremental update(?)
            System.out.println("Incremental update aborted due to running tasks");
            return null;
        }

        if (!site.isDateFormatSet()) {
            return "Incremental update disabled: unknown server DateFormat";
        }

        String lastChanged = site.getLastModifiedTicketTimeIfKnown();

        // Disable incremental updates if the change-time detection fails. (Don't flood the server
        // with full-download requests each time the application comes into view.)
        if (site.hasConnected() && lastChanged == null) {
            return "Incremental update disabled: change timestamps not found";
        }

        System.out.println("Last changed ticket:" + lastChanged);
        slurp(lastChanged);
        return null;
    }

    /**
     * Terminate any in-progress slurp task. It may carry on for a while - but it will discard its
     * results. (No need to wait for it.)
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
        task.executeWithNotification(taskClearer);
    }
}
