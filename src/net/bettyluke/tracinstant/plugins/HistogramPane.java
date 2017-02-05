
package net.bettyluke.tracinstant.plugins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.bettyluke.util.swing.VerticallyScrollingPanel;

import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.util.swing.ArrayListModel;

public class HistogramPane {

    private static final Dimension PANEL_PREFERRED_SIZE = new Dimension(300, 50);
    private static final String[] DEFAULT_FIELDS = {
        "Milestone", "Reporter", "Owner", "Type", "Priority", "Component", "Version", "Severity"
    };

    public static ToolPlugin createPlugin() {
        return new HistogramPane().new Plugin();
    }

    /** Holder class fetches rendering hints the first time it is needed, and then caches it. */
    private static class HintHolder {
        static final Map<?,?> HINTS;
        static {
            Toolkit tk = Toolkit.getDefaultToolkit();
            Map<?,?> hints = (Map<?,?>) tk.getDesktopProperty("awt.font.desktophints");
            HINTS = hints == null ? Collections.emptyMap() : hints;
        }
    }

    private static final class Bar {
        private final String label;
        public int active = 0;
        public int closed = 0;
        public int selectedActive = 0;
        public int selectedClosed = 0;

        public Bar(String name) {
            label = name;
        }

        private int total() {
            return active + closed;
        }

        @Override
        public String toString() {
            return closed + " / " + total();
        }
    }

    private static final class RenderInfo {
        private int maxResultsInCategory;
        private int maxStrLen;

        public RenderInfo(Collection<Bar> bars) {
            for (Bar bar : bars) {
                int total = bar.total();
                if (maxResultsInCategory < total) {
                    maxResultsInCategory = total;
                }
                int strLen = bar.toString().length();
                if (maxStrLen < strLen) {
                    maxStrLen = strLen;
                }
            }
        }
    }

    private final JComboBox fieldSelector;
    private final JPanel mainPanel;
    private final JPanel histogram;
    private Ticket[] ticketsInView = new Ticket[0];
    private Ticket[] selectedTickets = new Ticket[0];
    private RenderInfo renderInfo = null;
    private JList labels;
    private JList histi;

    private static final class LabelRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, final Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            Component comp = super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            Bar bar = (Bar) value;
            JLabel label = (JLabel) comp;
            label.setHorizontalAlignment(RIGHT);
            label.setText(sanitiseLabel(bar.label));
            return comp;
        }

        private String sanitiseLabel(String text) {
            if (text.isEmpty()) {
                text = " ";
            }
            int at = text.indexOf("@");
            if (at >= 0 && at < text.length() - 4) {
                text = text.subSequence(0, at + 4) + "\u2026";
            }
            return text;
        }
    }

    private final class BarRenderer extends DefaultListCellRenderer {
        Bar bar = null;

        @Override
        public Component getListCellRendererComponent(JList list, final Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            bar = (Bar) value;
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth() - getPreferredSize().width;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.addRenderingHints(HintHolder.HINTS);

            float closedRatio = (float) bar.closed / renderInfo.maxResultsInCategory;
            float totalRatio = (float) bar.total() / renderInfo.maxResultsInCategory;
            float selClosedRatio = (float) bar.selectedClosed / renderInfo.maxResultsInCategory;
            float selActiveRatio = (float) bar.selectedActive / renderInfo.maxResultsInCategory;
            int xClosed = (int) (w * closedRatio);
            int xTotal = (int) (w * totalRatio);

            g2.setColor(Color.GREEN.darker());
            g2.fillRect(0, 5, xClosed, 12);
            g2.drawRect(0, 5, xTotal, 12);

            g2.setColor(new Color(96, 96, 224));
            g2.fillRect(0, 5, (int) (w * selClosedRatio), 13);
            g2.setColor(new Color(200, 200, 255));
            g2.fillRect(xClosed, 6, (int) (w * selActiveRatio), 11);

            g2.setColor(Color.RED.darker());
            g2.drawString(bar.toString(), xTotal + 6, 16);
        }
    }

    /** The interface through which the application interacts with us. */
    private class Plugin extends ToolPlugin {

        @Override
        public JComponent initialise(TicketUpdater tu) {
            return mainPanel;
        }

        @Override
        public void ticketViewUpdated(Ticket[] inView, Ticket[] selected) {
            ticketsInView = Arrays.copyOf(inView, inView.length);
            selectedTickets = Arrays.copyOf(selected, selected.length);
            updateHistogram();
        }

        @Override
        public String toString() {
            return "Histograms";
        }

        @Override
        public void hidden() {
            // TODO: Save last-used view preferences
        }
    }

    public HistogramPane() {
        fieldSelector = createFieldSelection();
        labels = new JList();
        labels.setCellRenderer(new LabelRenderer());

        histi = new JList();
        histi.setCellRenderer(new BarRenderer());

        histogram = VerticallyScrollingPanel.create(histi);
        histogram.add(labels, BorderLayout.WEST);

        JScrollPane scroll = new JScrollPane(histogram);
        scroll.getViewport().setBackground(Color.WHITE);
        mainPanel = createMainPanel(fieldSelector, scroll);
    }

    private JComboBox createFieldSelection() {
        JComboBox combo = new JComboBox(DEFAULT_FIELDS);
        combo.addItemListener(e -> updateHistogram());
        return combo;
    }

    private JPanel createMainPanel(JComponent north, JComponent centre) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(north, BorderLayout.NORTH);
        panel.add(new JScrollPane(centre));
        panel.setPreferredSize(PANEL_PREFERRED_SIZE);
        return panel;
    }

    private void updateHistogram() {
        String field = ((String) fieldSelector.getSelectedItem()).toLowerCase();
        Collection<Bar> bars = getBars(field).values();
        populateView(bars);
    }

    private Map<String, Bar> getBars(String field) {
        Map<String, Bar> results = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Ticket ticket : ticketsInView) {
            String value = ticket.getValue(field);
            if (value != null) {
                Bar bar = getOrCreate(results, value);
                if ("closed".equals(ticket.getValue("status"))) {
                    bar.closed++;
                } else {
                    bar.active++;
                }
            }
        }
        for (Ticket ticket : selectedTickets) {
            String value = ticket.getValue(field);
            if (value != null) {
                Bar bar = getOrCreate(results, value);
                if ("closed".equals(ticket.getValue("status"))) {
                    bar.selectedClosed++;
                } else {
                    bar.selectedActive++;
                }
            }
        }
        return results;
    }

    private Bar getOrCreate(Map<String, Bar> results, String value) {
        Bar bar = results.get(value);
        if (bar == null) {
            bar = new Bar(value);
            results.put(value, bar);
        }
        return bar;
    }

    private void populateView(Collection<Bar> bars) {
        renderInfo = new RenderInfo(bars);

        ArrayListModel<Bar> model = ArrayListModel.of(bars);

        labels.setModel(model);
        histi.setModel(model);

        histogram.invalidate();
        histogram.revalidate();
        histogram.repaint();
    }
}
