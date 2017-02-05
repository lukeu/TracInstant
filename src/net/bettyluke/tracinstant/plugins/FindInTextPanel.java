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

package net.bettyluke.tracinstant.plugins;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import net.bettyluke.tracinstant.data.Ticket;

/**
 * Class containing the views and logic to:
 *  - locate ticket numbers in some (pasted) text,
 *  - further filter them according to the main user QUERY, and
 *  - format the result as an Outlook-style search query
 */
public class FindInTextPanel extends JPanel {

    public static ToolPlugin createPlugin() {
        return new FindInTextPanel().new Plugin();
    }

    /** The interface through which the application interacts with us. */
    private class Plugin extends ToolPlugin {

        @Override
        public JComponent initialise(TicketUpdater updater) {
            return FindInTextPanel.this;
        }

        @Override
        public void ticketViewUpdated(Ticket[] inView, Ticket[] selected) {
            filter.clear();
            for (Ticket ticket : inView) {
                filter.add(ticket.getNumber());
            }
            updateOutputFields();
        }

        @Override
        public String toString() {
            return "Find tickets in text";
        }
    }

    private static final Pattern TICKET_PATTERN = Pattern.compile("\\#(\\d+)");

    /**
     * Class that performs a single action 'later', following one or more modifications in the
     * processing of a single EDT event.
     */
    private static final class DocChangeListener implements DocumentListener {
        private final Runnable wrappedRunner;
        private boolean pending = false;

        public DocChangeListener(final Runnable runnable) {
            wrappedRunner = new Runnable() {
                public void run() {
                    runnable.run();
                    pending = false;
                }
            };
        }

        public void removeUpdate(DocumentEvent e) {
            maybeRun();
        }

        public void insertUpdate(DocumentEvent e) {
            maybeRun();
        }

        public void changedUpdate(DocumentEvent e) {
            maybeRun();
        }

        private void maybeRun() {
            if (!pending) {
                pending = true;
                SwingUtilities.invokeLater(wrappedRunner);
            }
        }
    }

    private JTextComponent sourceTextEditor = createTextArea();
    private Set<Integer> ticketsInText = Collections.emptySet();
    private JTextComponent foundTicketNumbersArea = createTextArea();
    private JTextComponent filteredTicketNumbersArea = createTextArea();
    private final Set<Integer> filter = new TreeSet<>();

    private static JTextComponent createTextArea() {
        JTextComponent ta = new JTextArea();
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        return ta;
    }

    public FindInTextPanel() {
        super(new BorderLayout());
        JScrollPane scroll1 = new JScrollPane(foundTicketNumbersArea);
        JScrollPane scroll2 = new JScrollPane(filteredTicketNumbersArea);
        scroll1.setPreferredSize(new Dimension(50, 50));
        scroll2.setPreferredSize(new Dimension(50, 50));
        Box grid = Box.createVerticalBox();
        grid.add(new JLabel("Tickets found in the text above..."));
        grid.add(scroll1);
        grid.add(new JLabel("Filtered Tickets (in Outlook Query format)..."));
        grid.add(scroll2);

        add(new JLabel("Paste text to scan for ticket numbers:"), BorderLayout.NORTH);
        add(createSplit(new JScrollPane(sourceTextEditor), grid));

        sourceTextEditor.getDocument().addDocumentListener(new DocChangeListener(new Runnable() {
            public void run() {
                ticketsInText = scanText(sourceTextEditor.getText());
                updateOutputFields();
            }
        }));
    }

    private void updateOutputFields() {
        String newFoundText = formatFoundTicketText(ticketsInText);
        if (!newFoundText.equals(foundTicketNumbersArea.getText())) {
            foundTicketNumbersArea.setText(newFoundText);
        }

        Set<Integer> intersection = new TreeSet<>(ticketsInText);
        intersection.retainAll(filter);
        String newFilteredText = formatOutlookQuery(intersection);
        if (!newFilteredText.equals(filteredTicketNumbersArea.getText())) {
            filteredTicketNumbersArea.setText(newFilteredText);
        }
    }

    private JSplitPane createSplit(JComponent top, JComponent bottom) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, top, bottom);
        split.setResizeWeight(.5f);
        return split;
    }

    /**
     * Prints ticket numbers as a (multiple) search queries that Microsoft Outlook can
     * handle. It can only handle short strings!
     */
    protected String formatOutlookQuery(Set<Integer> tickets) {
        int count = 0;
        StringBuilder sb = new StringBuilder("subject:(");
        for (int ticket : tickets) {
            if (count == 8) {
                sb.append(")\nsubject:(");
                count = 0;
            } else if (count > 0) {
                sb.append(" OR ");
            }
            sb.append(ticket);
            ++count;
        }
        sb.append(')');
        return sb.toString();
    }

    protected final Set<Integer> scanText(String text) {
        Set<Integer> result = new TreeSet<>();
        Matcher m = TICKET_PATTERN.matcher(text);
        while (m.find()) {
            try {
                result.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ex) {
                // Ignore. Probably just overflow.
            }
        }
        return result;
    }

    protected final String formatFoundTicketText(Set<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        String separator = "#:^(";
        for (int i : numbers) {
            sb.append(separator).append(i);
            separator = "|";
        }
        if (sb.length() > 0) {
            sb.append(")$");
        }
        return sb.toString();
    }

    /** A little interactive test. */
    public static void main(String[] args) {
        JDialog dialog = new JDialog();
        dialog.getContentPane().add(new FindInTextPanel());
        dialog.setSize(300, 600);
        dialog.setModal(true);
        dialog.setVisible(true);
        System.exit(0);
    }
}
