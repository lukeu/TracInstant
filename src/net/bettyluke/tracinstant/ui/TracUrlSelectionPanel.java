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

import java.awt.Dimension;
import java.awt.Window;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import net.bettyluke.tracinstant.prefs.SiteSettings;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;

public class TracUrlSelectionPanel extends JPanel {

    private final JComboBox url = createCombo();
    private final JComboBox attachmentsDir = createCombo();
    private final JCheckBox fetchActiveTickets = new JCheckBox(
        "Fetch only active tickets");
    private final JCheckBox instantRestart = new JCheckBox(
        "Instant restart (store downloaded data locally)");
    
    private static JComboBox createCombo() {
       JComboBox combo = new JComboBox();
       combo.setPreferredSize(new Dimension(400, combo.getPreferredSize().height));
       combo.setEditable(true);
       return combo;
    }
    
    public SiteSettings showAsDialog(Window parentWindow) {
        int opt = JOptionPane.showConfirmDialog(
            parentWindow,
            this,
            "Trac server settings",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        if (opt != JOptionPane.OK_OPTION) {
            return null;
        }

        // Record the selected button for text time.
        TracInstantProperties.get().putInt("MasterQuery", opt);
        
        SiteSettings result = new SiteSettings();
        result.setURL(getURLText());
        result.setAttachmentsDir(getAttachmentsDirText());
        result.setCacheData(instantRestart.isSelected());
        result.setFetchOnlyActiveTickets(fetchActiveTickets.isSelected());
        return result;
    }
    
    public TracUrlSelectionPanel(SiteSettings settings) {
        
        fetchActiveTickets.setSelected(settings.isFetchOnlyActiveTickets());
        instantRestart.setSelected(settings.isCacheData());
        
        // Probably redundant in correct usage, but here just in case public 'set' 
        // methods aren't called...
        url.setSelectedItem(TracInstantProperties.getURL());
        attachmentsDir.setSelectedItem(TracInstantProperties.getAttachmentsDir());

        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        add(Box.createVerticalStrut(8));
        addRow("Trac url:", url);
        add(Box.createVerticalStrut(8));
        addRow("Additional attachments folder: (optional)", attachmentsDir);
        add(Box.createVerticalStrut(16));
        add(fetchActiveTickets);
        add(Box.createVerticalStrut(8));
        add(instantRestart);
        add(Box.createVerticalStrut(16));

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                url.requestFocusInWindow();
            }
        });
    }

    private void addRow(String title, JComponent component) {
        JLabel label = new JLabel(title);
        label.setLabelFor(component);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setOpaque(true);
        
        // Fix alignment inconsistencies
        label.setAlignmentX(0f);
        component.setAlignmentX(0f);
        
        add(label);
        add(Box.createHorizontalStrut(2));
        add(component);
    }
    
    private String getURLText() {
        Object item = url.getSelectedItem();
        String text = (item == null) ? "" : ((String) item).trim();
        if (!text.toLowerCase().startsWith("http:")) {
            return "http://" + text;
        }
        return text;
    }
    
    public void setURLHistory(List<String> items) {
        url.removeAllItems();
        for (String item : items) {
            url.addItem(item);
        }
    }
    
    private String getAttachmentsDirText() {
        Object text = attachmentsDir.getSelectedItem();
        return (text == null) ? "" : ((String) text).trim();
    }
    
    public void setAttachmentsDirHistory(List<String> items) {
        attachmentsDir.removeAllItems();
        for (String item : items) {
            attachmentsDir.addItem(item);
        }
    }
    
    public void setInstantResart(boolean b) {
        instantRestart.setSelected(b);
    }
    
    public void setFetchOnlyActiveTickets(boolean b) {
        fetchActiveTickets.setSelected(b);
    }
}
