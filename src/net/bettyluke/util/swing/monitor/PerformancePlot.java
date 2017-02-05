/*
 * Copyright 2014 Luke Usherwood.
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

package net.bettyluke.util.swing.monitor;

import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

import net.bettyluke.util.swing.monitor.EdtMonitorModel.StatListener;

public class PerformancePlot extends JTable {

    private static final Color BACKGROUND_COLOR = new Color(12, 12, 48);

    public static class PlotModel extends AbstractTableModel  {
        EdtMonitorModel dataModel;
        public PlotModel(EdtMonitorModel model) {
            dataModel = model;
        }
        @Override
        public int getRowCount() {
            return 1;
        }
        @Override
        public int getColumnCount() {
            return dataModel.statBins.length;
        }
        @Override
        public PeriodStatistics getValueAt(int rowIndex, int columnIndex) {
            return dataModel.getBin(getColumnCount() - columnIndex - 1);
        }
        void fireChange() {
            fireTableChanged(new TableModelEvent(
                    PlotModel.this, 0, 0, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
        }
    }

    public static class TableController implements StatListener {
        private final PerformancePlot plot;
        public TableController(PerformancePlot plot) {
            this.plot = plot;
        }

        /**
         * Auto-attach/detach the controller to the plot's data-model when the table is showing.
         * As well as stopping doing work while it is hidden, this ensures the listener upon the
         * {@link EdtMonitorModel} doesn't linger, holding everything in memory after the view has
         * been closed/disposed.
         */
        public void autoAttachListeners() {
            plot.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (plot.isShowing()) {
                        attachListener();
                    } else {
                        detachListener();
                    }
                }
            });
        }
        public void attachListener() {
            plot.getModel().dataModel.addStatListener(this);
            plot.getModel().fireChange();
        }
        public void detachListener() {
            plot.getModel().dataModel.removeStatListener(this);
        }
        public void statsUpdate(int updateCount) {
            plot.setUpdateInProgress(true);
            try {
                ListSelectionModel selection = plot.getColumnModel().getSelectionModel();
                int lead = Math.max(0, selection.getLeadSelectionIndex() - updateCount);
                int anchor = Math.max(0, selection.getAnchorSelectionIndex() - updateCount);
                plot.getModel().fireChange();

                int min = selection.getMinSelectionIndex() - updateCount;
                int max = selection.getMaxSelectionIndex() - updateCount;
                if (max < 0) {
                    selection.clearSelection();
                } else {

                    // Note there is a mystical ordering dependency here, solved by experimentation,
                    // to keep the selection updated accurately while dragging both left-to-right
                    // and right-to-left.
                    selection.setLeadSelectionIndex(lead);
                    selection.setSelectionInterval(Math.max(min, 0), max);
                    selection.setAnchorSelectionIndex(anchor);
                }
            } finally {
                plot.setUpdateInProgress(false);
            }
        }
    }

    private boolean updateInProgress;

    public PerformancePlot(EdtMonitorModel model) {
        super(new PlotModel(model));
        setDefaultRenderer(Object.class, new PercentageTimeTableRenderer());
        setRowMargin(0);
        TableColumnModel columns = getColumnModel();
        columns.setColumnSelectionAllowed(true);
        columns.setColumnMargin(1);
        columns.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        setBackground(BACKGROUND_COLOR);
        setGridColor(getBackground());
        for (int i = 0; i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setMinWidth(1);
        }

        TableController controller = new TableController(this);
        controller.autoAttachListeners();
        addListenerToShowOrHideGrid();
    }

    private void addListenerToShowOrHideGrid() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {

                // TODO: Magic numbers
                getColumnModel().setColumnMargin(getWidth() > 400 ? 1 : 0);
                setRowHeight(getHeight());
            }
        });
    }

    public void setUpdateInProgress(boolean b) {
        updateInProgress = b;
    }

    public boolean isUpdateInProgress() {
        return updateInProgress;
    }

    @Override
    public PlotModel getModel() {
        return (PlotModel) super.getModel();
    }
}
