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

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Parses the XML export from Trac, in particular to populate the HTML description. */
public class TracXmlTicketParser {

    private static final Set<String> TICKET_FIELDS = new TreeSet<>(
        Arrays.asList(new String[] { "title", "description"}));

    private static final Pattern TICKET_URL_NUMBER_FINDER =
        Pattern.compile(".*\\/(([0-9])+)\\/?$");
    /** Never constructed */
    private TracXmlTicketParser() {}

    public static TracXmlResult parse(InputSource src) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = builder.parse(src);
            return readRSS(dom);
        } catch (DOMException | FactoryConfigurationError | ParserConfigurationException exc) {
            exc.printStackTrace();
        }
        throw new IOException("DOM error");
    }

    private static TracXmlResult readRSS(Document dom) throws IOException {
        Element rss = dom.getDocumentElement();
        String rssTagName = rss.getTagName();
        if (!"rss".equals(rssTagName)) {
            throw new IOException(
                "Document does not appear to be an RSS document. " +
                "Expected the XML root to be " +
                "'rss', but found: " + rssTagName);
        }

        for (Element child : DOMUtils.iterateChildElements(rss)) {
            if (child.getTagName() == "channel") {
                return readChannel(child);
            }
        }
        throw new IOException("Expected a single child of 'rss' node named 'channel'.");
    }

    private static TracXmlResult readChannel(Element channel) throws IOException {
        TracXmlResult data = new TracXmlResult();
        for (Element child : DOMUtils.iterateChildElements(channel)) {

            if ("item".equals(child.getTagName())) {
                Ticket ticket = readItem(child);
                if (ticket != null) {
                    data.addTicket(ticket);
                }
            } else if ("title".equals(child.getTagName())) {
                data.setTitle(child.getTextContent());
            } else if ("link".equals(child.getTagName())) {
                data.setLink(child.getTextContent());
            } else if ("description".equals(child.getTagName())) {
                data.setDescription(child.getTextContent());
            }

        }
        return data;
    }

    private static Ticket readItem(Element item) throws IOException {
        int number = extractTicketNumber(item);
        Ticket ticket = new Ticket(number);
        for (Element child : DOMUtils.iterateChildElements(item)) {
            String tag = child.getTagName();
            if (TICKET_FIELDS.contains(tag)) {

                // For now, we just set the Ticket's field name to the XML tag
                ticket.putField(tag, new String(child.getTextContent()));
            }
        }
        return ticket;
    }

    private static int extractTicketNumber(Element item) throws IOException {

        // Could equally use the Title.
        Element linkElement = DOMUtils.findFirstChildElementNamed(item, "link");
        if (linkElement == null) {
            throw new IOException("Invalid Ticket in RSS - no link found");
        }
        String link = linkElement.getTextContent();
        Matcher m = TICKET_URL_NUMBER_FINDER.matcher(link);
        if (m.matches()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ex) {
            }
        }
        throw new IOException("Link doesn't look like a Trac ticket: " + link);
    }
}
