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
        
package net.bettyluke.tracinstant.ui;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;


public class HtmlFormatter {
    
    private static final int MAX_DESCRIPTIONS = 50;

    private static final Pattern BUG_PATTERN = Pattern.compile("#([0-9]{1,8}+)");

    private static final URL STYLESHEET_TRAC_RESOURCE =
        HtmlFormatter.class.getResource("res/trac.css");
    
    private final static String HTML_HEADER =
        "<html><head><link rel=\"stylesheet\" type=\"text/css\" href=\"" +
            STYLESHEET_TRAC_RESOURCE + "\">";
    
    private final static String HTML_END = "</html>";

    /**
     * Remove a style that Java can't display, so that closed tickets display
     * crossed out
     */
    protected static String fixHyperlinks(String text) {
        return text.replaceAll("class=\\\"closed ticket", "class=\"closed");
    }

    protected static String buildDescription(Ticket[] tickets, SearchTerm[] searchTerms) {
        if (tickets.length == 0) {
            return "";
        }
        String body = buildBody(tickets);
        String highlighted = highlightMatches(body, searchTerms);
        return HTML_HEADER + fixHyperlinks(highlighted) + HTML_END;
    }

    private static String buildBody(Ticket[] tickets) {
        StringBuilder body = new StringBuilder("<body style=\"margin:0;\">");
        int count = 0;
        for (Ticket ticket : tickets) {
            if ((++count) > MAX_DESCRIPTIONS) {
                body.append("<br><i>Limit of " + MAX_DESCRIPTIONS + " tickets reached.");
                break;
            }
            String background = (count % 2 == 0) ? "#ffffd0" : "#ffffff";
            body.append("<div style=\"background:" + background + "; padding:3px;\">");
            String heading = makeHyperlinkedHeading(ticket);
            if (heading != null) {
                body.append(heading);
            }
            String description = ticket.getValue("description");
            if (description == null) {
                body.append(
                    "<br><i>Trac query in progress...</i><br> &nbsp;");
                body.append("</div>");
                break;
            }
            body.append(description);
            body.append("</div>");
        }
        body.append("</body>");
        return body.toString();
    }

    private static String highlightMatches(String body, SearchTerm[] searchTerms) {
        Pattern superPattern = createSuperPattern(searchTerms);
        if (superPattern == null) {
            return body;
        }
        
        StringBuilder bb = new StringBuilder();
        int rangeStart = 0, rangeEnd = 0, replacements = 0;
        while (true) {
            rangeEnd = body.indexOf('>', rangeStart) + 1;
            if (rangeEnd == -1) {
                break;
            }
            
            // Append up to end of a tag without modification.
            bb.append(body.substring(rangeStart, rangeEnd));
            rangeStart = rangeEnd;
            
            rangeEnd = body.indexOf('<', rangeStart);
            if (rangeEnd == -1) {
                break;
            }
            
            // Append a text segment with any matching highlighting marked-up
            String text = body.substring(rangeStart, rangeEnd);
            Matcher m = superPattern.matcher(text);
            
            replacements += appendHighlightedText(bb, m, text);

            // Limit to a reasonable number of highlights; not just so that this method
            // doesn't spiral out of control (time, memory) but also the HTML renderer.
            if (replacements > 800) {
                break;
            }
            
            rangeStart = rangeEnd;
        }
        bb.append(body.substring(rangeStart, body.length()));
        return bb.toString();
    }

    /** Like <code>Matcher.replaceAll()</code> but also counting replacements. */
    private static int appendHighlightedText(StringBuilder bb, Matcher m, String text) {
        int replacements = 0;
        if (m.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                m.appendReplacement(sb, 
                    "<font color=\"white\" bgcolor=\"#66dd88\">$0</font>");
                ++ replacements;
            } while (m.find());
            m.appendTail(sb);
            bb.append(sb.toString());
        } else {
            bb.append(text);
        }
        return replacements;
    }

    private static Pattern createSuperPattern(SearchTerm[] searchTerms) {
        StringBuilder sb = new StringBuilder();
        String pipe = "";
        sb.append('(');
        for (SearchTerm term : searchTerms) {
            
            // TODO: create constants for special fields such as "description"
            if (term.field == null ||
                    "description".startsWith(term.field.toLowerCase())) {
                sb.append(pipe).append(term.pattern.toString());
                pipe = "|";
            }
        }
        sb.append(')');
        if (sb.length() == 2) {
            return null;
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static String makeHyperlinkedHeading(Ticket ticket) {
        String title = ticket.getValue("title");
        if (title == null) {
            return null;
        }
        title = BUG_PATTERN.matcher(title).replaceAll(
            "<a style=\"text-decoration: none;\" " +
            "href=\"" + TracInstantProperties.getURL() + "/ticket/$1\">#$1</a>");
        return "<h2 style=\"color:#770044;\">" + title + "</h2>";
    }
}
