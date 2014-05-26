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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import net.bettyluke.tracinstant.data.SavedSearch;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;

/** 
 * NB: Most of the customisation is in SearchComboEditor. This class mainly ties a 
 * few parts together. 
 */
public class SearchCombo extends JComboBox {
    
    private final class DropDownRenderer extends DefaultListCellRenderer {

        JPanel panel = new JPanel(new BorderLayout());
        Box box = Box.createHorizontalBox();
        JLabel desc = new JLabel(" ");
        JLabel alias = new JLabel(" ");
        JLabel searchText = new JLabel(" ");
        Font monoFont = new Font(Font.MONOSPACED, Font.BOLD, 12);
        Font descFont = desc.getFont().deriveFont(desc.getFont().getSize() + 2f);
        
        public DropDownRenderer() {
            desc.setBackground(TRANSPARENT);
            desc.setFont(descFont);
            desc.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            
            alias.setBackground(TRANSPARENT);
            alias.setFont(monoFont);

            searchText.setBackground(TRANSPARENT);
            searchText.setFont(monoFont);
            
            box.add(alias);
            box.add(searchText);
            box.add(Box.createGlue());
            box.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 6));

            panel.add(desc, BorderLayout.NORTH);
            panel.add(box, BorderLayout.SOUTH);
            panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        }

        @Override
        public Component getListCellRendererComponent(
                JList list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            SavedSearch ss = (SavedSearch) value;
            
            Color fg = isSelected ? 
                list.getSelectionForeground() : list.getForeground();

            desc.setText(ss.name.isEmpty() ? "" : ss.name);
            desc.setForeground(fg);
            
            alias.setText(ss.alias);
            alias.setForeground(isSelected ? fg : Color.BLUE);

            String searchBase = ss.alias.isEmpty() ? "" : " : ";
            searchText.setText(searchBase + ss.searchText);
            searchText.setForeground(isSelected ? fg : Color.GRAY);
            
            Color bg = isSelected ? list.getSelectionBackground() : TRANSPARENT;
            panel.setBackground(bg);
            panel.setOpaque(isSelected);
            return panel;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
        }
    }

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    public SearchCombo() {
        super(new SearchComboBoxModel());
        setEditable(true);

        setEditor(new BasicComboBoxEditor() {
            @Override
            protected JTextField createEditorComponent() {
                return new SearchComboEditor(getModel(), "", 9);
            }
            
            @Override
            public Component getEditorComponent() {
                Component editorComponent = super.getEditorComponent();
                return editorComponent;
            }
        });
        
        SearchComboEditor ed = getEditorComponent();
        ed.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        
        setPreferredSize(getPreferredSize());
        changeListRenderer();
        loadLastSearch();
    }
    
    protected final String getText() {
        try {
            Document doc = getEditorComponent().getDocument();
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void changeListRenderer() {
        setRenderer(new DropDownRenderer());
    }
    
    @Override
    public SearchComboBoxModel getModel() {
        return (SearchComboBoxModel) super.getModel();
    }

    @Override
    public SavedSearch getItemAt(int index) {
        return getModel().getElementAt(index);
    }

    public String getEditorText() {
        return getEditorComponent().getText();
    }

    public void setEditorText(String text) {
        getEditorComponent().setText(text);
    }

    public SearchComboEditor getEditorComponent() { 
        return (SearchComboEditor) getEditor().getEditorComponent();
    }

    /** Get the text, with all saved shorthands expanded. */ 
    public String getExpandedText() {
        
        TreeMap<String, SavedSearch> map = new TreeMap<String,SavedSearch>(
            String.CASE_INSENSITIVE_ORDER);
        int num = getModel().getSize();
        for (int i = 0; i < num; i++) {
            SavedSearch ss = getModel().getElementAt(i);
            if (!ss.alias.isEmpty()) {
                map.put(ss.alias, ss);
            }
        }
        
        StringBuilder result = new StringBuilder();
        String space = "";
        
        // Perhaps this class should just be returning data, rather than processing it,
        // but it seems a little easier to do this here.
        final String[] words = getEditorText().split("\\s");
        for (int i = 0; i < words.length; i++) {
            result.append(space);
            space = " ";
            SavedSearch ss = map.get(words[i]);
            if (ss == null) {
                result.append(words[i]);
            } else {
                result.append(ss.searchText);
            }
        }

        return result.toString();
    }
    
    private void loadLastSearch() {
        setEditorText(TracInstantProperties.get().getValue("CurrentFilter"));
    }

    public void saveSearches() {
        TracInstantProperties.get().putString("CurrentFilter", getEditorText());
        getModel().saveSavedSearches();
    }
}
