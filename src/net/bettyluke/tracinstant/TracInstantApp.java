/*
 * Copyright 2011 Luke Usherwood.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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
        
package net.bettyluke.tracinstant;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import net.bettyluke.tracinstant.data.CachedTicketLoadTask;
import net.bettyluke.tracinstant.data.SiteData;
import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.tracinstant.data.TicketLoadTask;
import net.bettyluke.tracinstant.plugins.AnnotationPanel;
import net.bettyluke.tracinstant.plugins.FindInTextPanel;
import net.bettyluke.tracinstant.prefs.SiteSettings;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;
import net.bettyluke.tracinstant.ui.TracInstantFrame;


public final class TracInstantApp {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TracInstantApp().startOnEDT();
            }
        });
    }
    
    public TracInstantApp() {
        TracInstantProperties.initialise("bettyluke.net", "TracInstant");
    }
    
    public void startOnEDT() {
        setLaF();
        final SiteData site = new SiteData();
        final TracInstantFrame frame = new TracInstantFrame(site);
        frame.installToolPanel(AnnotationPanel.createPlugin());
        frame.installToolPanel(FindInTextPanel.createPlugin());
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
            TicketLoadTask loadTask = new CachedTicketLoadTask(site.getTableModel());
            frame.monitorTask(loadTask);
            loadTask.executeWithNotification(new Runnable() {
                public void run() {
                    loadServerTickets(frame, site);
                }
            });
        } else {
            
            // Proceed with next step immediately.
            loadServerTickets(frame, site);
        }
    }

    private void loadServerTickets(TracInstantFrame frame, final SiteData site) {
        
        // HACK
        frame.getSlurpAction().setEnabled(true);
        
        Ticket[] tickets = site.getTableModel().getTickets();
        if (tickets.length == 0) {
            if (frame.getSlurpAction().promptForTracSettings()) {
                frame.getSlurpAction().performAction();
            } else {
                frame.dispose();
            }
            return;
        }
        frame.getSlurpAction().slurpIncremental(SiteSettings.fromPreferences(), tickets);
        site.loadUserData();
    }

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
