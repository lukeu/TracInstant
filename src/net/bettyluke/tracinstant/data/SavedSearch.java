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
        
package net.bettyluke.tracinstant.data;

public class SavedSearch {
    private static final String MARKER = "~::~";
    public final String searchText;
    public final String alias;
    public final String name;

    public SavedSearch(String searchText, String alias, String name) {
        if (alias == null || name == null || searchText == null) {
            throw new NullPointerException();
        }
        this.searchText = searchText.trim();
        this.alias = alias.trim();
        this.name = name.trim();
    }

    /** Construct by parsing a melded string as used in the Application Properties. */
    public SavedSearch(String str) {
        int pos;
        pos = str.indexOf(MARKER);
        if (pos >= 0) {
            alias = str.substring(0, pos).trim();
            str = str.substring(pos + MARKER.length());
        } else {
            alias = "";
        }   
        pos = str.indexOf(MARKER);
        if (pos >= 0) {
            name = str.substring(0, pos).trim();
            str = str.substring(pos + MARKER.length());
        } else {
            name = "";
        }
        searchText = str.trim();
    }
    
    public String formatAsPreference() {
        return alias + MARKER + name + MARKER + searchText;
    }
    
    public String getSearchText() {
        return searchText;
    }
    
    public String getAlias() {
        return alias;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return searchText;
    }
    
    /** 
     * Required by BasicComboBoxEditor#getItem ... OBVIOUSLY. (Called reflectively.) 
     */
    public static SavedSearch valueOf(String searchText) {
        return new SavedSearch(searchText);
    }
}
