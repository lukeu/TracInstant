
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

package com.github.tracinstant.app.plugins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.HighlightPainter;
import javax.swing.text.JTextComponent;

import com.github.swingdpi.UiScaling;
import com.github.tracinstant.app.data.Ticket;

/**
 * Class containing the views and logic to:
 *  - locate ticket numbers in some (pasted) text,
 *  - further filter them according to the main user QUERY, and
 *  - format the result as an Outlook-style search query
 */
public class FindInTextPanel extends JPanel {

    private static final HighlightPainter HIGHLIGHTER = new DefaultHighlightPainter(Color.YELLOW);

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
            updateFields();
        }

        @Override
        public String toString() {
            return "Find tickets in text";
        }
    }

    private static final Pattern TICKET_PATTERN = Pattern.compile("\\#(\\d+)");

    private final Map<String, Function<Set<Integer>, String>> formatters =
            new LinkedHashMap<String, Function<Set<Integer>, String>>() {{
        put("TracInstant search term", FindInTextPanel::formatFoundTicketText);
        put("Outlook search (short)", ints -> formatOutlookQuery(ints, 8));
        put("Outlook search (long)", ints -> formatOutlookQuery(ints, 22));
    }};

    /**
     * Class that performs a single action 'later', following one or more modifications in the
     * processing of a single EDT event.
     */
    private static final class DocChangeListener implements DocumentListener {
        private final Runnable wrappedRunner;
        private boolean pending = false;

        public DocChangeListener(final Runnable runnable) {
            wrappedRunner = () -> {
                runnable.run();
                pending = false;
            };
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            maybeRun();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            maybeRun();
        }

        @Override
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

    private final JTextArea sourceTextEditor = createTextArea();
    private final JTextComponent resultArea = createTextArea();
    private final Set<Integer> filter = new TreeSet<>();
    private final JComboBox<String> resultCombo;
    private final JCheckBox filterCheck;
    private Set<Integer> ticketsInText = Collections.emptySet();

    private static JTextArea createTextArea() {
        JTextArea ta = new JTextArea();
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UiScaling.scale(14)));
        return ta;
    }

    public FindInTextPanel() {
        super(new BorderLayout());
        JScrollPane scroll1 = new JScrollPane(sourceTextEditor);
        JScrollPane scroll2 = new JScrollPane(resultArea);
        Dimension size = UiScaling.newDimension(50, 50);
        scroll1.setPreferredSize(size);
        scroll2.setPreferredSize(size);

        filterCheck = new JCheckBox("Apply filter", true);
        resultCombo = new JComboBox<>(formatters.keySet().toArray(new String[0]));

        Box box = Box.createHorizontalBox();
        box.add(filterCheck);
        box.add(new JLabel("   Show tickets as: "));
        box.add(resultCombo);

        JPanel north = new JPanel(new BorderLayout());
        JPanel south= new JPanel(new BorderLayout());
        north.add(new JLabel("Paste text to scan for ticket numbers:"), BorderLayout.NORTH);
        north.add(scroll1);
        south.add(box, BorderLayout.NORTH);
        south.add(scroll2);

        add(createSplit(north, south));

        sourceTextEditor.getDocument().addDocumentListener(new DocChangeListener(() -> {
            ticketsInText = scanText(sourceTextEditor.getText());
            updateFields();
        }));

        filterCheck.addActionListener(l -> updateFields());
        resultCombo.addActionListener(l -> updateFields());
    }

    private void highlightSourceText() {
        Highlighter sourceHighlighter = sourceTextEditor.getHighlighter();
        sourceHighlighter.removeAllHighlights();
        Set<Integer> tickets = getFilteredTicketsInText();
        if (tickets.isEmpty()) {
            return;
        }

        Pattern pattern = Pattern.compile(asRegex(tickets));
        String text = sourceTextEditor.getText();
        Matcher m = pattern.matcher(text);
        try {
            while (m.find()) {
                int start = m.start();
                if (start > 0 && text.charAt(start - 1) == '#') {
                    start --;
                }
                sourceHighlighter.addHighlight(start, m.end(), HIGHLIGHTER);
            }
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private void updateFields() {
        highlightSourceText();
        updateOutputFields(formatters.get(resultCombo.getSelectedItem()));
    }

    private void updateOutputFields(Function<Set<Integer>, String> formatter) {
        Set<Integer> tickets = getFilteredTicketsInText();
        String newFoundText = formatter.apply(tickets);
        if (!newFoundText.equals(resultArea.getText())) {
            resultArea.setText(newFoundText);
        }
    }

    private Set<Integer> getFilteredTicketsInText() {
        Set<Integer> tickets = new TreeSet<>(ticketsInText);
        if (filterCheck.isSelected()) {
            tickets.retainAll(filter);
        }
        return tickets;
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
    private static String formatOutlookQuery(Set<Integer> tickets, int maxPerLine) {
        int count = 0;
        StringBuilder sb = new StringBuilder("subject:(");
        for (int ticket : tickets) {
            if (count == maxPerLine) {
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

    protected static final String formatFoundTicketText(Set<Integer> numbers) {
        return numbers.isEmpty() ? "" : "#:^(" + asRegex(numbers) + ")$";
    }

    private static String asRegex(Set<Integer> numbers) {
        return numbers.stream().map(n -> n.toString()).collect(Collectors.joining("|"));
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
