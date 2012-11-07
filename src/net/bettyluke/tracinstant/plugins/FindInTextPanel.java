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
    
    /** Implement the interface by which the Main Frame interacts with us. */
    public class Plugin extends ToolPlugin {

        @Override
        public JComponent initialise(TicketUpdater updater) {
            return FindInTextPanel.this;
        }

        @Override
        public void ticketViewUpdated(Ticket[] inView, Ticket[] selected) {
            m_Filter.clear();
            for (Ticket ticket : inView) {
                m_Filter.add(ticket.getNumber());
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
     * Class that performs a single action 'later', following one or more modifications
     * in the processing of a single EDT event.
     */
    private static final class DocChangeListener implements DocumentListener {
        private final Runnable m_WrappedRunner;
        private boolean m_Pending = false;

        public DocChangeListener(final Runnable runnable) {
            m_WrappedRunner = new Runnable() {
                public void run() {
                    runnable.run();
                    m_Pending = false;
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
            if (!m_Pending) {
                m_Pending = true;
                SwingUtilities.invokeLater(m_WrappedRunner);
            }
        }
    }

    private JTextComponent m_Text = createTextArea();
    private Set<Integer> m_TicketsInText = Collections.emptySet();
    private JTextComponent m_FoundTicketNumbers = createTextArea();
    private JTextComponent m_FilteredTicketNumbers = createTextArea();
    private final Set<Integer> m_Filter = new TreeSet<Integer>();

    private static JTextComponent createTextArea() {
        JTextComponent ta = new JTextArea();
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        return ta;
    }

    public FindInTextPanel() {
        super(new BorderLayout());
        JScrollPane scroll1 = new JScrollPane(m_FoundTicketNumbers);
        JScrollPane scroll2 = new JScrollPane(m_FilteredTicketNumbers);
        scroll1.setPreferredSize(new Dimension(50, 50));
        scroll2.setPreferredSize(new Dimension(50, 50));
        Box grid = Box.createVerticalBox();
        grid.add(new JLabel("Tickets found in the text above..."));
        grid.add(scroll1);
        grid.add(new JLabel("Filtered Tickets (in Outlook Query format)..."));
        grid.add(scroll2);
        
        add(new JLabel("Paste text to scan for ticket numbers:"), BorderLayout.NORTH);
        add(createSplit(new JScrollPane(m_Text), grid));
        
        m_Text.getDocument().addDocumentListener(new DocChangeListener(new Runnable() {
            public void run() {
                m_TicketsInText = scanText(m_Text.getText());
                updateOutputFields();
            }
        }));
    }
    
    private void updateOutputFields() {
        String newFoundText = formatFoundTicketText(m_TicketsInText);
        if (!newFoundText.equals(m_FoundTicketNumbers.getText())) {
            m_FoundTicketNumbers.setText(newFoundText);
        }
        
        Set<Integer> intersection = new TreeSet<Integer>(m_TicketsInText);
        intersection.retainAll(m_Filter);
        String newFilteredText = formatOutlookQuery(intersection);
        if (!newFilteredText.equals(m_FilteredTicketNumbers.getText())) {
            m_FilteredTicketNumbers.setText(newFilteredText);
        }
    }

    private JSplitPane createSplit(JComponent top, JComponent bottom) {
        JSplitPane split = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, true, top, bottom);
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
            ++ count;
        }
        sb.append(')');
        return sb.toString();
    }

    protected final Set<Integer> scanText(String text) {
        Set<Integer> result = new TreeSet<Integer>();
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
        dialog.setSize(300,600);
        dialog.setModal(true);
        dialog.setVisible(true);
        System.exit(0);
    }
}
