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

package com.github.tracinstant.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import com.github.tracinstant.app.data.Ticket;
import com.github.tracinstant.app.data.TicketTableModel;
import com.github.tracinstant.app.prefs.TracInstantProperties;
import com.github.tracinstant.app.ui.MenuCascader.Item;

public class TicketTable extends JTable {

    public static class ColumnWidthMemoriser {

        private static final String COLUMNS_KEY = "TicketTableColumns";

        /**
         * Basic defaults so that a few specific columns appear together on the left,
         * prior to being user-customised. TODO: Numbers are currently based on the
         * HACK (commented lower in the file) and will need revising.
         */
        private static final String COLUMNS_DEFAULT = "[#=65, summary=350, reporter=100]";

        private final JTableHeader tableHeader;

        public ColumnWidthMemoriser(JTableHeader header) {
            tableHeader = header;
        }

        public void attach() {
            tableHeader.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    memoriseColumnLayout();
                }
            });

            tableHeader.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
                @Override
                public void columnSelectionChanged(ListSelectionEvent e) {
                }

                @Override
                public void columnRemoved(TableColumnModelEvent e) {
                }

                @Override
                public void columnMoved(TableColumnModelEvent e) {
                }

                @Override
                public void columnMarginChanged(ChangeEvent e) {
                }

                @Override
                public void columnAdded(TableColumnModelEvent e) {
                    SwingUtilities.invokeLater(() -> recallColumnLayout());
                }
            });
        }

        protected void memoriseColumnLayout() {
            TableColumnModel cm = tableHeader.getColumnModel();
            List<String> headerWidths = new ArrayList<>();
            for (int i = 0; i < cm.getColumnCount(); ++i) {
                headerWidths.add(
                    cm.getColumn(i).getHeaderValue() + "=" +
                    cm.getColumn(i).getWidth());
            }
            TracInstantProperties.putStringList(COLUMNS_KEY, headerWidths);
        }

        protected void recallColumnLayout() {
            List<String> stored = TracInstantProperties.getStringList(
                COLUMNS_KEY, COLUMNS_DEFAULT);
            Map<String, Integer> widths = new LinkedHashMap<>(stored.size());
            try {
                for (String column : stored) {
                    String[] s = column.split("\\=");
                    if (s.length != 2) {
                        System.err.println("Invalid TicketTableColumns property");
                        continue;
                    }
                    widths.put(s[0], Integer.valueOf(s[1]));
                }
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
            }

            TableColumnModel cm = tableHeader.getColumnModel();
            int newIndex = 0;
            for (String colName : widths.keySet()) {
                int oldIndex = findColumnIndex(cm, newIndex, colName);
                if (oldIndex > -1) {
                    cm.moveColumn(oldIndex, newIndex);

                    // HACK! only a temporary sizing measure.
                    // TODO: Proportion correctly to the total size of the header.
                    // (then fix basic defaults!)
                    cm.getColumn(newIndex).setPreferredWidth(widths.get(colName));
                    newIndex++;
                }
            }
        }

        private int findColumnIndex(TableColumnModel cm, int from, String colName) {
            for (int i = 0; i < cm.getColumnCount(); i++) {
                if (colName.equals(cm.getColumn(i).getHeaderValue())) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * We simply store a reference to the main-frame's text component to update it directly.
     * (Perhaps it would be cleaner to access it via an interface.)
     */
    private final SearchCombo searchCombo;

    public TicketTable(TicketTableModel model, SearchCombo searchCombo) {
        super(model);
        this.searchCombo = searchCombo;
        setRowSorter(new TableRowSorter<>(model));
        addColumnContextMenu();
        ColumnWidthMemoriser cwm = new ColumnWidthMemoriser(getTableHeader());
        cwm.attach();
    }

    protected void addColumnContextMenu() {
        getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                if (e.getClickCount() == 1) {
                    int viewColumn = getColumnModel().getColumnIndexAtX(e.getX());
                    final JPopupMenu menu = createColumnFilterMenu(viewColumn);
                    menu.show(getTableHeader(), e.getX(), e.getY());
                }
            }
        });
    }

    private JPopupMenu createColumnFilterMenu(int viewColumn) {
        String colName = getColumnName(viewColumn);

        Map<String, Integer> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int r = 0, end = getRowCount(); r < end; ++r) {
            Object value = getValueAt(r, viewColumn);
            String[] strs = value.toString().split("[\\s\\,]+");
            for (String s : strs) {
                Integer count = map.get(s);
                map.put(s, (count == null) ? 1 : count + 1);
            }
        }

        List<Item> items = new ArrayList<>();
        for (Entry<String, Integer> s : map.entrySet()) {
            items.add(new FilterColumnAction(colName, s.getKey(), s.getValue()));
        }

        return new MenuCascader().create(items);
    }

    /** An action that adds a new search-term to m_FilterText when invoked. */
    public class FilterColumnAction extends AbstractAction implements MenuCascader.Item {
        private final String columnName;
        private final String item;
        private final int hitCount;

        public FilterColumnAction(String columnName, String item, int hitCount) {
            super(item + " (" + hitCount + ")");
            this.columnName = columnName;
            this.item = item;
            this.hitCount = hitCount;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String text = searchCombo.getEditorText();
            StringBuilder sb = new StringBuilder(text);
            sb.append(' ').append(columnName).append(':');
            sb.append(addWordBoundaries(escape(item)));
            searchCombo.setEditorText(sb.toString().trim());
            searchCombo.requestFocusInWindow();
        }

        private String escape(String text) {
            return text.replaceAll("([^0-9a-zA-Z_])", "\\\\$1");
        }

        private String addWordBoundaries(String text) {
            if (text.isEmpty()) {
                return text;
            }
            Pattern startPattern = Pattern.compile(Pattern.quote(text) + "\\B",
                    Pattern.CASE_INSENSITIVE);
            Pattern endPattern = Pattern.compile("\\B" + Pattern.quote(text),
                    Pattern.CASE_INSENSITIVE);

            int nTickets = getModel().getRowCount();
            boolean startMatch = false;
            boolean endMatch = false;
            for (int i = 0; i < nTickets; ++i) {
                Ticket ticket = getModel().getTicket(i);
                String value = ticket.getValue(columnName);
                startMatch |= startPattern.matcher(value).find();
                endMatch |= endPattern.matcher(value).find();
            }

            return (endMatch ? "\\b" : "") + text + (startMatch ? "\\b" : "");
        }

        @Override
        public String getName() {
            return item;
        }

        @Override
        public int getHits() {
            return hitCount;
        }
    }

    @Override
    public TicketTableModel getModel() {
        return (TicketTableModel) super.getModel();
    }

    @SuppressWarnings("unchecked")
    @Override
    public TableRowSorter<? extends TicketTableModel> getRowSorter() {
        return (TableRowSorter<? extends TicketTableModel>) super.getRowSorter();
    }
}
