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

import java.awt.Font;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import com.github.tracinstant.app.data.SavedSearch;
import com.github.tracinstant.app.prefs.TracInstantProperties;
import com.github.tracinstant.swing.SwingUtils;

/**
 * Most of the customisation code in SearchComboEditor, SearchDropDownRenderer.
 * This class mainly ties things together.
 */
public class SearchCombo extends JComboBox<SavedSearch> {

    public SearchCombo() {
        super(new SearchComboBoxModel());
        setEditable(true);

        setEditor(new BasicComboBoxEditor() {
            @Override
            protected JTextField createEditorComponent() {
                return new SearchComboEditor(getModel(), "", 9);
            }
        });

        SearchComboEditor ed = getEditorComponent();
        ed.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        SwingUtils.addUndoSupport(ed);

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
        setRenderer(new SearchDropDownRenderer());
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

    private void loadLastSearch() {
        setEditorText(TracInstantProperties.get().getValue("CurrentFilter"));
    }

    public void saveSearches() {
        TracInstantProperties.get().putString("CurrentFilter", getEditorText());
        getModel().saveSavedSearches();
    }
}
