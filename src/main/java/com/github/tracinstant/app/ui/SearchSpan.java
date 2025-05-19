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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;

/**
 * A span represents a run of one or more 'tokens'. Tokens are globbed together into a Span
 * so as to perform fewer text-rendering calls. This is not only for efficiency, but every text
 * break is an opportunity for rounding errors to creep in to the horizontal spacing due to the
 * use of JDK-8's int-based APIs being rendered on HiDPI monitors via newer JDKs at runtime.
 */
final class SearchSpan {

    enum Kind { TEXT, FIELD, NEGATE, ALIAS; }

    /**
     * Client code can assume this is NonNull. null is allowed (for whitespace) but such
     * objects are post-processed away by the parser before external code sees it.
     */
    final Kind kind;

    int end;

    SearchSpan(Kind col, int endOffset) {
        this.kind = col;
        this.end = endOffset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, end);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SearchSpan)) {
            return false;
        }
        SearchSpan other = (SearchSpan) obj;
        return kind == other.kind && end == other.end;
    }

    @Override
    public String toString() {
        return "Span(" + kind + ", " + end + ")";
    }

    public static List<SearchSpan> parse(SortedSet<String> aliases, String input) {
        return new Tokenizer(input).tokenize(aliases);
    }

    private static class Tokenizer {
        private final String input;
        private List<SearchSpan> tokens = new ArrayList<>();
        private boolean negating = false;

        Tokenizer(String input) {
            this.input = input;
        }

        List<SearchSpan> tokenize(SortedSet<String> aliases) {
            collectTokens();
            return expandAliasesAndGlob(aliases);
        }

        /**
         * Scan along each character of 'input', determining which token kind it represents.
         * Each time this changes, insert a 'span'. The exception is ':' which converts the
         * current token being collected into FIELD (instead of TEXT)
         */
        private void collectTokens() {
            int len = input.length();
            Kind kind = null;
            for (int i = 0; i < len; i++) {
                char ch = input.charAt(i);
                if (Character.isWhitespace(ch)) {
                    if (kind != null) {
                        addSpan(kind, i);
                        kind = null;
                    }
                    negating = false;
                } else if (ch == '-') {
                    if (kind != Kind.TEXT && kind != Kind.NEGATE) {
                        addSpan(kind, i);
                        kind = Kind.NEGATE;
                        negating = true;
                    }
                } else if (ch == ':') {
                    // Add the token being collected so far with kind = FIELD
                    tokens.add(new SearchSpan(kind == Kind.TEXT ? Kind.FIELD : kind, i));

                    // Mark ':' to also be an ALIAS, a separate token that'll glob into the above later
                    kind = Kind.FIELD;
                } else {
                    if (kind != Kind.TEXT) {
                        addSpan(kind, i);
                    }
                    // Collect this character word as text.
                    kind = Kind.TEXT;
                }
            }
            int last = tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).end;
            if (last != len) {
                addSpan(kind, len);
            }
        }

        private void addSpan(Kind kind, int i) {
            if (kind == Kind.TEXT && negating) {
                kind = Kind.NEGATE;
            }
            tokens.add(new SearchSpan(kind, i));
        }

        List<SearchSpan> expandAliasesAndGlob(SortedSet<String> aliases) {
            List<SearchSpan> result = new ArrayList<>();
            SearchSpan last = new SearchSpan(null, 0);
            for (SearchSpan token : tokens) {
                if (token.kind == Kind.TEXT
                        && (last.end == 0 || input.charAt(last.end - 1) != ':') 
                        && aliases.contains(input.substring(last.end, token.end))) {
                    token = new SearchSpan(Kind.ALIAS, token.end);
                }

                if (last.kind == null || token.kind == null || token.kind == last.kind) {
                    if (!result.isEmpty()) {
                        result.remove(result.size() - 1);
                    }
                    token = new SearchSpan(token.kind != null ? token.kind : last.kind, token.end);
                }

                result.add(token);
                last = token;
            }
            return result;
        }
    }
}
