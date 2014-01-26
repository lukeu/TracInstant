/*
 * Copyright 2014 Luke Usherwood.
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

package net.bettyluke.util.swing.monitor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class EdtMonitorPanel extends JPanel {

    private final EdtMonitorModel model;
    private final PerformancePlot plot;
    private final JTextArea text;
    private final JSplitPane split;

    public EdtMonitorPanel(EdtMonitorModel model) {
        super(new BorderLayout());

        this.model = model;
        plot = adjustSizes(new PerformancePlot(model));
        text = createText();
        JScrollPane scroll = adjustSizes(new JScrollPane(text));
        split = createSplit(plot, scroll);

        // TODO: add speed/pause controls

        add(split);

        updateTextAndFont(null);
        listenForSelectionChanges();
    }

    private static JTextArea createText() {
        JTextArea text = new JTextArea();
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
        return text;
    }

    private static JSplitPane createSplit(JComponent first, JComponent second) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, first, second);
        split.setPreferredSize(new Dimension(600, 460));
        split.setResizeWeight(1.0);
        return split;
    }

    private void listenForSelectionChanges() {
        plot.getColumnModel().getSelectionModel().addListSelectionListener(
                new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!plot.isUpdateInProgress()) {
                    updateTextArea();
                }
            }
        });
    }

    private void updateTextArea() {
        ListSelectionModel selection = plot.getColumnModel().getSelectionModel();
        int first = selection.getMinSelectionIndex();
        int last = selection.getMaxSelectionIndex();
        PeriodStatistics stats = (first == last) ? getStat(first) : mergeStats(first, last);
        updateTextAndFont(stats);
        text.setCaretPosition(0);
    }

    /**
     * @param stats Nullable
     */
    private void updateTextAndFont(PeriodStatistics stats) {
        if (shouldShowStats(stats)) {
            text.setText(stats.toString());
            text.setFont(text.getFont().deriveFont(Font.PLAIN));
        } else {
            text.setText("Select bar(s) to view details");
            text.setFont(text.getFont().deriveFont(Font.ITALIC));
        }
    }

    private boolean shouldShowStats(PeriodStatistics stats) {
        return stats != null && plot.getSelectedColumnCount() != 0;
    }

    /**
     * @return Can be null!
     */
    private PeriodStatistics getStat(int viewIndex) {
        return model.getBin(EdtMonitorModel.NUMBER_OF_BINS - viewIndex - 1);
    }

    private PeriodStatistics mergeStats(int firstIndex, int lastIndex) {
        assert lastIndex > firstIndex;
        PeriodStatistics summary = new PeriodStatistics(0L);
        for (int i = firstIndex; i <= lastIndex; ++i) {
            PeriodStatistics bin = getStat(i);
            if (bin != null) {
                summary.merge(bin);
            }
        }
        return summary;
    }

    private static <T extends JComponent> T adjustSizes(T comp) {
        comp.setPreferredSize(new Dimension(200,200));
        comp.setMinimumSize(new Dimension(10,10));
        return comp;
    }
}
