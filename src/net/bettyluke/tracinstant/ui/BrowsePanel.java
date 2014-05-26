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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

/** A general panel for displaying an editable path and a Browse button. */
public class BrowsePanel extends JPanel implements ActionListener {
    
    private final JTextField m_LocationEditor;

    public BrowsePanel(File folder) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        m_LocationEditor = new JTextField(folder.getAbsolutePath());
        m_LocationEditor.setPreferredSize(
            new Dimension(300, m_LocationEditor.getPreferredSize().height));
        JButton browse = new JButton("Browse...");
        browse.addActionListener(this);
        add(m_LocationEditor);
        add(Box.createHorizontalStrut(6));
        add(browse);
    }

    public File getPath() {
        return new File(m_LocationEditor.getText());
    }
    
    public JTextComponent getLocationEditor() {
        return m_LocationEditor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(getPath());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setPreferredSize(new Dimension(720,520));
        if (JFileChooser.APPROVE_OPTION ==
                chooser.showDialog(this, "Select this folder")) {
            m_LocationEditor.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    @Override
    public void setToolTipText(String text) {
        super.setToolTipText(text);
        for (Component comp : getComponents()) {
            if (comp instanceof JComponent) {
                JComponent jComp = (JComponent) comp;
                jComp.setToolTipText(text);
            }
        }
    }
}
