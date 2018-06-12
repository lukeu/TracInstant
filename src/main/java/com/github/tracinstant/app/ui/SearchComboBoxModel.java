
package com.github.tracinstant.app.ui;

import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.DefaultComboBoxModel;

import com.github.tracinstant.app.data.SavedSearch;
import com.github.tracinstant.app.prefs.TracInstantProperties;

/**
 * ComboBoxModel that does little other than try to enforce type safety
 * (SavedSearch instead of Object) a little, and saves/loads the data from App Prefs.
 */
public class SearchComboBoxModel extends DefaultComboBoxModel<SavedSearch> {

    private static final int MAX_SAVED_SEARCHES = 50;

    private TreeMap<Integer, SavedSearch> hiddenElements = new TreeMap<>();

    public SearchComboBoxModel() {
        loadSavedSearches();
    }

    private void loadSavedSearches() {
        clearPrefixFilter();
        for (int i = 0; i < MAX_SAVED_SEARCHES; ++i) {
            String str = TracInstantProperties.get().getValue("SearchHistory_" + i);
            if (str == null) {
                break;
            }
            addElement(new SavedSearch(str));
        }
        setSelectedItem(null);
    }

    public void saveSavedSearches() {
        clearPrefixFilter();
        int i = 0;
        for (; i < getSize(); ++i) {
            TracInstantProperties.get().putString("SearchHistory_" + i,
                    getElementAt(i).formatAsPreference());
        }
        for (; i < MAX_SAVED_SEARCHES; ++i) {
            TracInstantProperties.get().remove("SearchHistory_" + i);
        }
    }

    @Override
    public void setSelectedItem(Object anItem) {
        clearPrefixFilter();
        super.setSelectedItem(anItem);
    }

    @Override
    public void addElement(SavedSearch ss) {
        clearPrefixFilter();
        super.addElement(ss);
    }

    @Override
    public void insertElementAt(SavedSearch ss, int index) {
        clearPrefixFilter();
        super.insertElementAt(ss, index);
    }

    @Override
    public SavedSearch getElementAt(int index) {
        return super.getElementAt(index);
    }

    public void updateSearch(SavedSearch ss) {
        clearPrefixFilter();
        int count = getSize();
        for (int i = 0; i < count; ++i) {
            if (getElementAt(i).searchText.equalsIgnoreCase(ss.searchText)) {
                removeElementAt(i--);
                count--;
            }
        }
        insertElementAt(ss, 0);
        setSelectedItem(ss);
    }

    public SavedSearch findSearch(String text) {
        text = text.trim();
        int count = getSize();

        // First priority: matching the full search text
        for (int i = 0; i < count; ++i) {
            SavedSearch ss = getElementAt(i);
            if (ss.searchText.equalsIgnoreCase(text)) {
                return ss;
            }
        }
        // Second priority: matching an alias
        for (int i = 0; i < count; ++i) {
            SavedSearch ss = getElementAt(i);
            if (!ss.alias.isEmpty() && ss.alias.equalsIgnoreCase(text)) {
                return ss;
            }
        }
        return null;
    }

    public void clearPrefixFilter() {
        for (Entry<Integer, SavedSearch> entry : hiddenElements.entrySet()) {
            super.insertElementAt(entry.getValue(), entry.getKey());
        }
        hiddenElements.clear();
    }

    public void setPrefixFilter(String prefix) {
        clearPrefixFilter();
        if (prefix == null) {
            return;
        }
        prefix = prefix.toUpperCase();
        int count = getSize();
        for (int i = count - 1; i >= 0; --i) {
            String alias = getElementAt(i).alias;
            if (alias == null || !alias.toUpperCase().startsWith(prefix)) {
                hiddenElements.put(i, getElementAt(i));
                removeElementAt(i);
            }
        }
    }
}
