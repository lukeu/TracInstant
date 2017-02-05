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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;

import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.tracinstant.data.TicketTableModel;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;
import net.bettyluke.util.DesktopUtils;

public class HtmlDescriptionPane extends JEditorPane {

    private String lastDescriptionText = "";

    private static final class MyHyperlinkListener implements HyperlinkListener {

        private final Pattern TICKET_URL_PATTERN =
            Pattern.compile(".*/ticket/(\\d+)");
        private TicketTableModel m_TicketModel;

        public MyHyperlinkListener(TicketTableModel ticketModel) {
            m_TicketModel = ticketModel;
        }

        public void hyperlinkUpdate(HyperlinkEvent evt) {
            JEditorPane pane = (JEditorPane) evt.getSource();
            EventType type = evt.getEventType();
            if (type == HyperlinkEvent.EventType.ENTERED) {
                String urlString = evt.getDescription();
                pane.setToolTipText(enhanceTooltips(urlString));
            } else if (type == HyperlinkEvent.EventType.EXITED) {
                pane.setToolTipText(null);
            } else if (type == HyperlinkEvent.EventType.ACTIVATED) {
                DesktopUtils.browseTo(evt.getURL());
            }
        }

        private String enhanceTooltips(String original) {
            Matcher m = TICKET_URL_PATTERN.matcher(original);
            if (!m.matches()) {
                return original;
            }
            int id = Integer.parseInt(m.group(1));
            Ticket ticket = m_TicketModel.findTicketByID(id);
            if (ticket == null) {
                return original;
            }
            String title = ticket.getValue("title");
            return (title == null) ? original : title;
        }
    }

    public HtmlDescriptionPane(TicketTableModel ticketModel) {
        super("text/html", "");
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        setEditable(false);
        addHyperlinkListener(new MyHyperlinkListener(ticketModel));
    }

    // Update description if anything (even just the highlighting) changed.
    public void updateDescription(String text) {
        if (!text.equals(lastDescriptionText)) {
            lastDescriptionText = text;
            setText(text);
            setCaretPosition(0);
        }
    }

    public static void browseToTickets(Ticket[] tickets) throws MalformedURLException {
        String baseUrl = TracInstantProperties.getURL();
        int count = tickets.length;
        if (count == 1) {

            String query = baseUrl + "/ticket/" + tickets[0].getNumber();
            DesktopUtils.browseTo(new URL(query));
            return;
        }

        if (count > 1) {
            count = Math.min(300, count);

            StringBuilder sb = new StringBuilder();
            sb.append(baseUrl).append("/query?id=");
            String joint = "";
            for (int i = 0; i < count; ++i) {
                sb.append(joint).append(tickets[i].getNumber());
                joint = ",";
            }
            DesktopUtils.browseTo(new URL(sb.toString()));
            return;
        }
    }
}
