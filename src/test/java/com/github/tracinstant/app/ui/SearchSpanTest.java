/*
 * Copyright 2025 the original author or authors.
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

import static com.github.tracinstant.app.ui.SearchSpan.Kind.ALIAS;
import static com.github.tracinstant.app.ui.SearchSpan.Kind.FIELD;
import static com.github.tracinstant.app.ui.SearchSpan.Kind.NEGATE;
import static com.github.tracinstant.app.ui.SearchSpan.Kind.TEXT;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Test;

public class SearchSpanTest {

    @Test
    public void testParse() {
        SortedSet<String> aliases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        aliases.add("short");
        aliases.add("cut");

        assertEquals(Arrays.asList(), SearchSpan.parse(aliases, ""));
        assertEquals(Arrays.asList(span(TEXT, 3)), SearchSpan.parse(aliases, "abc"));
        assertEquals(Arrays.asList(span(TEXT, 5)), SearchSpan.parse(aliases, "a-c d"));
        assertEquals(
                Arrays.asList(span(ALIAS, 5), span(TEXT, 6)),
                SearchSpan.parse(aliases, "cut  d"));
        assertEquals(
                Arrays.asList(span(FIELD, 6), span(TEXT, 12)),
                SearchSpan.parse(aliases, "field:t-ex-t"));
        assertEquals(
                Arrays.asList(span(NEGATE, 5)),
                SearchSpan.parse(aliases, "-not "));
        assertEquals(
                Arrays.asList(span(FIELD, 7), span(TEXT, 12)),
                SearchSpan.parse(aliases, " field:short"));
        assertEquals(
                Arrays.asList(span(FIELD, 7), span(NEGATE, 12)),
                SearchSpan.parse(aliases, " field:-text"));
        assertEquals(
                Arrays.asList(span(NEGATE, 2), span(FIELD, 4), span(NEGATE, 8), span(ALIAS, 18)),
                SearchSpan.parse(aliases, " -f:not cut  short"));
        assertEquals(
                Arrays.asList(
                        span(ALIAS, 7),
                        span(NEGATE, 8), span(FIELD, 10), span(NEGATE, 14),
                        span(ALIAS, 18)),
                SearchSpan.parse(aliases, " short -f:n-t cut "));
        assertEquals(
                Arrays.asList(span(NEGATE, 7)),
                SearchSpan.parse(aliases, " -short"));
    }

    private static SearchSpan span(SearchSpan.Kind kind, int end) {
        return new SearchSpan(kind, end);
    }
}
