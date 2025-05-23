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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.tracinstant.app.data.SavedSearch;

/** One of a user's search term, in the format: <code>[-][field:][-]pattern</code> */
final class SearchTerm {

    private static final Pattern EMPTY_STRING_PATTERN = Pattern.compile("^$");

    public SearchTerm(String field, Pattern pattern, boolean exclude) {
        this.field = field;
        this.pattern = pattern;
        this.exclude = exclude;
    }

    /** The optional field name; null to search all fields. */
    public String field;

    /** The search pattern */
    public Pattern pattern;

    /**
     * If true, the Ticket will only be matched if the pattern is NOT found in the
     * ticket's field(s)
     */
    public boolean exclude;

    @Override
    public String toString() {
        return "SearchTerm [field=" + field + ", pattern=" + pattern + ", exclude="
            + exclude + "]";
    }

    public static List<SearchTerm> parseSearchString(
            SortedMap<String, SavedSearch> shorthands, String searchText) {

        if (searchText.trim().isEmpty()) {
            return TableRowFilterComputer.EMPTY_SEARCH_TERMS;
        }

        return Arrays.stream(searchText.split("\\s"))
                .flatMap(w -> {
                    SavedSearch expanded = shorthands.get(w);
                    return expanded == null
                            ? Stream.of(w)
                            : Arrays.stream(expanded.searchText.split("\\s"));
                })
                .map(w -> parseTerm(w))
                .filter(Objects::nonNull)

                // Do what we can for filtering speed - apply a partial sorting
                // to make terms with specific fields come first.
                .sorted((t1, t2) -> {
                    int len1 = t1.field == null ? 0 : t1.field.length();
                    int len2 = t2.field == null ? 0 : t2.field.length();
                    return len2 - len1;
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** @return a SearchTerm, or null if the search pattern would be empty. */
    public static SearchTerm parseTerm(String word) {
        boolean exclude = word.startsWith("-");
        if (exclude) {
            word = word.substring(1);
        }
        String field = null;
        int colon = word.indexOf(':');
        if (colon != -1) {
            field = word.substring(0, colon);
            word = word.substring(colon + 1);
        }
        if (word.startsWith("-")) {
            exclude = true;
            word = word.substring(1);
        }
        if (word.isEmpty()) {
            if (field == null) {
                return null;
            } else {
                return new SearchTerm(field, EMPTY_STRING_PATTERN, exclude);
            }
        }
        try {
            Pattern regex = Pattern.compile(word, Pattern.CASE_INSENSITIVE);
            return new SearchTerm(field, regex, exclude);
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }
}
