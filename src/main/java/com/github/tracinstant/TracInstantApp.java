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

package com.github.tracinstant;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import com.github.tracinstant.app.data.CachedTicketLoadTask;
import com.github.tracinstant.app.data.SiteData;
import com.github.tracinstant.app.data.Ticket;
import com.github.tracinstant.app.data.TicketLoadTask;
import com.github.tracinstant.app.plugins.AnnotationPanel;
import com.github.tracinstant.app.plugins.FindInTextPanel;
import com.github.tracinstant.app.plugins.HistogramPane;
import com.github.tracinstant.app.prefs.SiteSettings;
import com.github.tracinstant.app.prefs.TracInstantProperties;
import com.github.tracinstant.app.ui.SlurpAction;
import com.github.tracinstant.app.ui.TracInstantFrame;

public final class TracInstantApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TracInstantApp().startOnEDT());
    }

    public TracInstantApp() {
        TracInstantProperties.initialise("bettyluke.net", "TracInstant");
    }

    public void startOnEDT() {
        setLaF();
        final SiteData site = new SiteData();

        // In truth this is a HACK that wipes out Trac 1.0 data for in-house users who have
        // updated to Trac 1.2 around the same time that I'm adding password support. Users at
        // other companies (which currently amount to a total around zero) will have to click the
        // "Connect to" button themselves if they end up having duplicated columns, etc. due to the
        // field renaming that occurred. (I don't currently have a mechanism to detect Trac versions
        // that would be robust in the face of customised ticket fields.)
        if (!TracInstantProperties.hasPasswordSupport()) {
            site.reset();
        }

        final TracInstantFrame frame = new TracInstantFrame(site);
        frame.installToolPanel(AnnotationPanel.createPlugin());
        frame.installToolPanel(FindInTextPanel.createPlugin());
        frame.installToolPanel(HistogramPane.createPlugin());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                frame.getSlurpAction().cancel();
                frame.dispose();
                site.saveState();
                saveApplicationState();
            }
        });

        // HACK: Cache-loading should be pretty darn fast, nevertheless it will not be
        // cancelled by a user selecting a different Trac server and starting a new
        // "slurp". So: we currently just disable the action.
        // (TODO: Create new 'TicketLoader' to more-cleanly manage loading from the 4
        // data sources, and the cancellation of these tasks.)

        frame.getSlurpAction().setEnabled(false);
        frame.setVisible(true);

        if (site.isOkToUseCachedTickets()) {
            TicketLoadTask loadTask = new CachedTicketLoadTask(site);
            frame.monitorTask(loadTask);
            loadTask.executeWithNotification(() -> loadServerTickets(frame, site));
        } else {

            // Proceed with next step immediately.
            loadServerTickets(frame, site);
        }
    }

    private void loadServerTickets(TracInstantFrame frame, final SiteData site) {
        // HACK
        SlurpAction slurper = frame.getSlurpAction();
        slurper.setEnabled(true);

        Authenticator.setDefault(SITE_AUTHENTICATOR);
        if (shouldPromptOnStartup(site)) {
            if (slurper.promptForTracSettings()) {
                slurper.slurpAll();
            } else {
                frame.dispose();
            }
        } else {
            site.loadUserData();
            String error = slurper.slurpIncremental();
            if (error != null) {

                // Allow a one-time full slurp all to cover the possibility that we don't know the
                // date format (after which incremental slurps will be disabled).
                slurper.slurpAll();
            }
        }
    }

    public boolean shouldPromptOnStartup(SiteData site) {
        if (!TracInstantProperties.hasPasswordSupport() ||
                !TracInstantProperties.getUsername().isEmpty() &&
                !TracInstantProperties.getRememberPassword()) {
            return true;
        }
        Ticket[] tickets = site.getTableModel().getTickets();
        return tickets.length == 0;
    }

    private static final Authenticator SITE_AUTHENTICATOR = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            SiteSettings ss = SiteSettings.getInstance();
            return new PasswordAuthentication(ss.getUsername(), ss.getPassword().toCharArray());
        }
    };

    private void saveApplicationState() {
        try {
            TracInstantProperties.get().saveProperties();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setLaF() {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            // If the unexpected happens, just roll with the defaults.
        }
    }
}
