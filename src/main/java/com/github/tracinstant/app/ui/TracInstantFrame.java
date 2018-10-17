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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;

import com.github.tracinstant.app.data.SiteData;
import com.github.tracinstant.app.data.Ticket;
import com.github.tracinstant.app.data.TicketLoadTask;
import com.github.tracinstant.app.data.TicketLoadTask.Update;
import com.github.tracinstant.app.data.TicketTableModel;
import com.github.tracinstant.app.download.DownloadDialog;
import com.github.tracinstant.app.download.DownloadModel;
import com.github.tracinstant.app.plugins.DummyPlugin;
import com.github.tracinstant.app.plugins.TicketUpdater;
import com.github.tracinstant.app.plugins.ToolPlugin;
import com.github.tracinstant.app.prefs.TracInstantProperties;
import com.github.tracinstant.swing.StatusWidget;
import com.github.tracinstant.util.DesktopUtils;
import com.github.tracinstant.util.DocUtils;
import com.github.tracinstant.util.FileUtils;

public class TracInstantFrame extends JFrame {

    private static final String FRAME_STATE_PROPERTY = "MainFrame";

    private static final Icon BUSY_IMAGE = StatusWidget.BUSY_IMAGE;

    private static final InputStream TIP =
        TracInstantFrame.class.getResourceAsStream("res/SearchTip.html");

    private static final int GAP = 6;

    private final class TicketLoadListener implements PropertyChangeListener {
        private final TicketLoadTask task;

        private TicketLoadListener(TicketLoadTask task) {
            this.task = task;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!evt.getPropertyName().equals("status")) {
                return;
            }
            removeWindowListener(m_OnActivationRefresher);

            Update update = (Update) evt.getNewValue();
            if (update.ticketProvider != null) {
                mergeTickets(update.ticketProvider.getTickets());
                return;
            }
            if (update.summaryMessage != null) {
                m_SlurpStatus.showBusy(update.summaryMessage, update.detailMessage);
                System.out.println(update.detailMessage);
            } else {
                task.removePropertyChangeListener(this);
                addWindowListener(m_OnActivationRefresher);

                // Retrieve any exceptions. (There is no "result" to collect, since
                // all data is processed on-the-fly via the publishing mechanism.)
                try {
                    try {
                        task.get();
                    } catch (CancellationException ex) {
                        // Treat the same as complete.
                    }
                    m_SlurpStatus.hide();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.interrupted();
                } catch (ExecutionException e) {
                    Throwable why = e.getCause();
                    Runnable doSlurp = () -> slurpAction.slurpIncremental();
                    if (why instanceof ConnectException) {
                        m_SlurpStatus.showRetryError(
                                "Disconnected (click to retry)", e.getMessage(), doSlurp);
                    } else if (why instanceof ProtocolException) {
                        m_SlurpStatus.showError(
                                "Not connected: Incorrect Password?", e.getMessage());
                    } else {
                        m_SlurpStatus.showRetryError(
                                "Not connected - hover for details", e.getMessage(), doSlurp);
                    }
                    e.printStackTrace();
                }

                System.out.println("Slurp complete.");
            }
            updateRowFilter();
            updateViews();
        }

        private void mergeTickets(List<Ticket> tickets) {
            m_Table.getModel().mergeTickets(tickets);

            /*
             * Performance! When the major sort criteria have lots of repeating strings (like sort
             * by Priority then Severity then Resolution), performance can crumble. This 'tweak'
             * foregoes full locale-sensitive sorting to gain performance. (RowSorter defaults to
             * "Collator" as the comparator for String columns). This speeds up the sorting of the
             * given scenario 6-fold, measuring a 10,000 ticket sort 600ms -> 100ms. Good find this!
             * After all that work and multi-threading to get the text searching down to sub 50 ms
             * (typical) it was a real shame that this Swing table-sort had become the bottleneck!
             */
            for (int col = 1; col < m_Table.getColumnCount(); col++) {
                m_Table.getRowSorter().setComparator(col, String.CASE_INSENSITIVE_ORDER);
            }
        }
    }

    private final class DownloadAction extends AbstractAction {
        private DownloadDialog downloadDialog;

        public DownloadAction() {
            super("Download...");
            this.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_D);
        }

        @Override
        public void actionPerformed(ActionEvent e) {

            if (downloadDialog == null) {
                if (m_Downloads.getBugsFolder() == null) {
                    m_Downloads.setBugsFolder(
                        TracInstantProperties.get().getFilePath(
                            "LocalBugsDir", new File("C:\\bugs")));
                }
                downloadDialog = new DownloadDialog(TracInstantFrame.this, m_Downloads);
            }
            downloadDialog.setVisible(true);
        }
    }

    private final class TicketSelectionListener implements ListSelectionListener {
        private final Timer m_Timer = new Timer(60, e2 -> updateViews());

        public TicketSelectionListener() {
            m_Timer.setRepeats(false);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {

            /*-
             * IGNORE an "adjusting" event while typing in the Search field:
             *   - this will always be zero rows, prior to re-sorting/re-filtering,
             *     so we want to avoid any annoying flicker.
             * HONOUR an "adjusting" event during a mouse-drag, for better UI feedback
             */
            if (m_RowFilterJustUpdated && e.getValueIsAdjusting()) {
                return;
            }
            m_RowFilterJustUpdated = false;

            // Add a slight delay for multiple rows. It could be due to mouse-drag,
            // SHIFT+arrow or updated row-filter. In any case, this is a simple trick
            // to reduce the frequency of description updates in it is expensive.
            m_Timer.setInitialDelay((m_Table.getSelectedRowCount() == 1) ? 5 : 60);
            m_Timer.restart();
        }
    }

    private WindowAdapter m_OnActivationRefresher = new UpdateTicketsOnWindowActivated();

    private class UpdateTicketsOnWindowActivated extends WindowAdapter {
        @Override
        public void windowActivated(WindowEvent e) {
            String problem = slurpAction.slurpIncremental();
            if (problem != null) {
                m_SlurpStatus.showWarning(problem, null);
            }
        }
    }

    private final TicketTable m_Table;
    private final HtmlDescriptionPane m_DescriptionPane;
    private final JSplitPane m_ToolWindowSplit;

    private final SearchCombo m_FilterCombo;
    private final JComboBox<ToolPlugin> m_PluginCombo;
    private final JLabel m_Matches;
    private final StatusWidget m_SlurpStatus = new StatusWidget();

    private final JLabel m_DownloadsNumber;
    private final Action m_DownloadAction = new DownloadAction();

    private final Box m_StatusPanel;

    private SearchTerm[] m_SearchTerms = new SearchTerm[0];

    /** One of those horrible flags you wish didn't need to exist. Just see the code. */
    private boolean m_RowFilterJustUpdated = false;

    private final DownloadModel m_Downloads;

    private final TableRowFilterComputer m_FilterComputor = new TableRowFilterComputer();

    /**
     * The set of selected tickets taken from the table and currently in display (by the description
     * panel, downloads util, and any other views plugins). These displays may update a short time
     * after the table itself has updated, for UI feedback responsiveness.
     * <p>
     * It is intended mainly to short-circuit evaluation (don't update if no work is needed); it is
     * particularly desirable to stop download-info requests hitting network resources
     * unnecessarily.
     */
    private Ticket[] m_DisplayedTickets = new Ticket[0];

    private Map<ToolPlugin, JComponent> m_Plugins = new HashMap<>();
    private ToolPlugin m_ActivePlugin = null;

    private final SlurpAction slurpAction;
    private final Action performanceAction = new ShowPerformanceMonitorAction(this);

    private final JSplitPane m_MainArea;

    private TicketUpdater m_TableModelUpdater = new TicketUpdater() {
        @Override
        public void setTicketField(int ticketId, String field, String value) {
            Ticket t = m_Table.getModel().findTicketByID(ticketId);
            if (t == null) {
                System.out.println("Ticket ID not found: " + ticketId);
                return;
            }
            t.putField(field, value);
        }

        @Override
        public void identifyUserField(String fieldName, boolean isUserDefined) {
            if (isUserDefined) {
                m_Table.getModel().addUserField(fieldName);
            } else {
                m_Table.getModel().removeUserField(fieldName);
            }
        }
    };

    public TracInstantFrame(SiteData site) {
        super("Trac Instant");
        slurpAction = new SlurpAction(this, site);
        m_FilterCombo = createFilterBox();
        JLabel filterLabel = createLabel("Filter: ", 'F', m_FilterCombo);

        m_Table = createTicketTable(site.getTableModel(), m_FilterCombo);

        m_DescriptionPane = new HtmlDescriptionPane(m_Table.getModel());

        ToolTipManager.sharedInstance().registerComponent(m_DescriptionPane);
        ToolTipManager.sharedInstance().setDismissDelay(60000);

        m_Matches = new JLabel();
        m_Matches.setPreferredSize(new Dimension(110, m_Matches.getPreferredSize().height));

        m_PluginCombo = createPluginCombo();
        JLabel pluginLabel = createLabel("Tools:", 'T', m_PluginCombo);

        Box toolPanel = createToolPanel(filterLabel, m_FilterCombo, m_Matches,
                createNewTicketButton(), pluginLabel, m_PluginCombo);

        m_DownloadsNumber = new JLabel("", null, SwingConstants.LEFT);
        m_DownloadsNumber.setHorizontalTextPosition(SwingConstants.LEFT);
        m_Downloads = createDownloadModel();

        m_StatusPanel = createStatusPanel(
            m_DownloadsNumber, new JButton(m_DownloadAction),
            Box.createHorizontalGlue(),
            m_SlurpStatus.getComponent(),
            new JButton(slurpAction));

        m_MainArea = createMainSplitArea(m_Table, m_DescriptionPane, m_StatusPanel);
        m_ToolWindowSplit = createToolSplit();

        Container cp = getContentPane();
        cp.setLayout(new BorderLayout());
        cp.add(toolPanel, BorderLayout.NORTH);
        cp.add(m_MainArea);

        FrameStatePersister wsp = new FrameStatePersister(FRAME_STATE_PROPERTY, this);
        wsp.restoreFrameState();
        wsp.startListening();
    }

    private JButton createNewTicketButton() {
        String text = "New&nbsp;ticket";
        String tooltip = "Create a new Trac ticket using an external web browser";
        ActionListener action = e -> {
            String baseUrl = TracInstantProperties.getURL();
            if (baseUrl != null) {
                try {
                    DesktopUtils.browseTo(new URL(baseUrl + "/newticket"));
                } catch (MalformedURLException ex) {
                    ex.printStackTrace();
                }
            }
        };
        JButton button = GuiUtilities.createHyperlinkButton(text, tooltip, action);
        GuiUtilities.makeMaxASmidgeWider(button, GAP);
        return button;
    }

    private JSplitPane createToolSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, null, null);
        split.setDividerLocation(1.0);
        split.setResizeWeight(1.0);
        return split;
    }

    private SearchCombo createFilterBox() {
        final SearchCombo result = new SearchCombo();

        addFilterAccelerator(result);

        JTextComponent editorComp = result.getEditorComponent();
        editorComp.getDocument().addDocumentListener(
                DocUtils.newOnAnyEventListener(() ->
                        SwingUtilities.invokeLater(() -> updateRowFilter())));

        editorComp.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isAltDown() &&
                        !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
                    m_Table.requestFocusInWindow();
                    if (m_Table.getRowCount() > 0) {
                        m_Table.getSelectionModel().setSelectionInterval(0, 0);
                    }
                }
            }
        });

        try {
            result.setToolTipText(FileUtils.copyInputStreamToString(TIP, "ISO-8859-1"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Probably only suitable for some layout managers. Works with current: "Box".
        result.setMinimumSize(new Dimension(8, 8));

        return result;
    }

    private void addFilterAccelerator(final SearchCombo result) {
        JComponent ancestor = (JComponent) getContentPane();
        ancestor.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "Select Filter");
        ancestor.getActionMap().put("Select Filter", new AbstractAction("Select Filter") {
            @Override
            public void actionPerformed(ActionEvent e) {
                result.requestFocusInWindow();
                result.getEditorComponent().selectAll();
            }
        });
    }

    private JLabel createLabel(String name, char mnemonic, JComboBox<?> boundComponent) {
        JLabel result = new JLabel(name);
        result.setDisplayedMnemonic(mnemonic);
        result.setLabelFor(boundComponent);
        return result;
    }

    private JComboBox<ToolPlugin> createPluginCombo() {
        JComboBox<ToolPlugin> combo = new JComboBox<>();
        combo.addItem(new DummyPlugin("None"));
        combo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                switchPlugin((ToolPlugin) e.getItem());
            }
        });
        GuiUtilities.makeMaxASmidgeWider(combo, GAP);
        return combo;
    }

    protected void switchPlugin(ToolPlugin plugin) {
        boolean activate = plugin != null && !(plugin instanceof DummyPlugin);
        if (activate) {
            if (m_ActivePlugin == null) {
                getContentPane().remove(m_MainArea);
                getContentPane().add(m_ToolWindowSplit);
                m_ToolWindowSplit.setLeftComponent(m_MainArea);
            } else {
                m_ActivePlugin.hidden();
            }
            m_ToolWindowSplit.setRightComponent(m_Plugins.get(plugin));
            m_ActivePlugin = plugin;
            m_ActivePlugin.shown();
            updatePlugin();
        } else {
            if (m_ActivePlugin != null) {
                m_ToolWindowSplit.setRightComponent(null);
                getContentPane().remove(m_ToolWindowSplit);
                getContentPane().add(m_MainArea);
                m_ActivePlugin.hidden();
                m_ActivePlugin = null;
            }
        }
        validate();
    }

    private TicketTable createTicketTable(TicketTableModel model, SearchCombo filter) {
        final TicketTable table = new TicketTable(model, filter);
        table.getSelectionModel().addListSelectionListener(new TicketSelectionListener());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 2) {
                    browseToSelectedTickets();
                    evt.consume();
                }
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent evt) {
                if (!KeyEvent.getKeyModifiersText(evt.getModifiers()).isEmpty()) {
                    return;
                }
                switch (evt.getKeyCode()) {
                case KeyEvent.VK_ENTER:
                    browseToSelectedTickets();
                    evt.consume();
                    break;
                case KeyEvent.VK_DELETE:
                    removeSelectedTicketsFromTable();
                    evt.consume();
                    break;
                default:
                    break;
                }
            }
        });

        return table;
    }

    private DownloadModel createDownloadModel() {
        DownloadModel result = new DownloadModel();
        result.addChangeListener(e -> {
            m_DownloadsNumber.setIcon(m_Downloads.isBusy() ? BUSY_IMAGE : null);
            m_DownloadsNumber.setText(m_Downloads.getDownloadSummary());
        });
        return result;
    }

    private static JSplitPane createMainSplitArea(
            TicketTable table, JEditorPane descriptionPane, Box statusPanel) {
        JPanel descriptionAndStatus = new JPanel(new BorderLayout());
        descriptionAndStatus.add(new JScrollPane(descriptionPane));
        descriptionAndStatus.add(statusPanel, BorderLayout.SOUTH);
        return createSplit(new JScrollPane(table), descriptionAndStatus);
    }

    private Box createToolPanel(JComponent... comps) {
        Box box = Box.createHorizontalBox();
        for (JComponent comp : comps) {
            box.add(Box.createHorizontalStrut(GAP));
            box.add(comp);
        }
        box.add(Box.createHorizontalStrut(GAP));
        return box;
    }

    private Box createStatusPanel(JLabel downloadNumber, Component... comps) {

        final Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalStrut(GAP));
        box.add(new JLabel("Attachments: "));
        box.add(downloadNumber);

        // Extra space here
        box.add(Box.createHorizontalStrut(GAP));

        for (Component comp : comps) {
            box.add(Box.createHorizontalStrut(GAP));
            box.add(comp);
        }

        box.add(createSneakyPerformanceMonitorButton(box, comps));

        return box;
    }

    private JComponent createSneakyPerformanceMonitorButton(final Box box, Component... comps) {
        final JButton pi = GuiUtilities.createHyperlinkButton("", null, performanceAction);
        pi.setText("  \u03C0  ");
        pi.setVisible(false);
        MouseAdapter listener = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                pi.setVisible(e.isControlDown() && e.isShiftDown());
            }
        };
        pi.addMouseMotionListener(listener);
        box.addMouseMotionListener(listener);
        for (Component comp : comps) {
            comp.addMouseMotionListener(listener);
        }
        return pi;
    }

    private static JSplitPane createSplit(JComponent top, JComponent bottom) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, top, bottom);

        // This is crap. Want the split bar to stay __where it is put__ even when
        // resized to a postage stamp size and back. Oh well, best we can do easily.
        split.setResizeWeight(0.3);
        split.setDividerLocation(0.3);
        return split;
    }

    protected void browseToSelectedTickets() {
        Ticket[] tickets = getSelectedTickets();
        try {
            HtmlDescriptionPane.browseToTickets(tickets);
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
    }

    protected void removeSelectedTicketsFromTable() {
        Ticket[] tickets = getSelectedTickets();
        String oldSearch = m_FilterCombo.getEditorText().trim();
        String newSearch = isLastTermADeletion(oldSearch) ?
            expandDeletionTerm(oldSearch, tickets) :
            appendDeletionTerm(oldSearch, tickets);

        // Attempt to retain selection even after deleting rows (and regenerating table)
        int oldRow = m_Table.getSelectionModel().getMaxSelectionIndex();
        if (oldRow < m_Table.getRowCount() - 1) {
            m_Table.getSelectionModel().setSelectionInterval(oldRow + 1, oldRow + 1);
        } else if (oldRow > 0) {
            m_Table.getSelectionModel().setSelectionInterval(oldRow - 1, oldRow - 1);
        }

        // This will fire change events...
        m_FilterCombo.setEditorText(newSearch.trim());
    }

    private static final Pattern DELETE_PATTERN = Pattern.compile(
        "\\-\\#\\:\\^\\(\\d+(\\|\\d+)*\\)\\$");

    private boolean isLastTermADeletion(String search) {
        String[] split = search.split("\\s");
        return split.length != 0 &&
            DELETE_PATTERN.matcher(split[split.length - 1]).matches();
    }

    private String expandDeletionTerm(String old, Ticket[] tickets) {
        assert old.length() > 6;
        StringBuilder sb = new StringBuilder(old.substring(0, old.length() - 2));
        for (Ticket ticket : tickets) {
            sb.append("|").append(ticket.getNumber());
        }
        sb.append(")$");
        return sb.toString();
    }

    private String appendDeletionTerm(String old, Ticket[] tickets) {
        StringBuilder sb = new StringBuilder(old);
        sb.append(" -#:^(");
        String pipe = "";
        for (Ticket ticket : tickets) {
            sb.append(pipe).append(ticket.getNumber());
            pipe = "|";
        }
        sb.append(")$");
        return sb.toString();
    }

    public void monitorTask(final TicketLoadTask task) {
        task.addPropertyChangeListener(new TicketLoadListener(task));
    }

    private void updateRowFilter() {

        // We set the search terms straight away, but don't fire any event so they won't
        // be applied until the view changes - typically when the search completes. It
        // could also happen earlier from a list-navigation event, but I think that's
        // fine; why not highlight matches in the description before the filtering is
        // complete anyway?
        m_SearchTerms = SearchTerm.parseSearchString(m_FilterCombo.getExpandedText());

        Ticket[] tickets = m_Table.getModel().getTickets();
        m_FilterComputor.computeFilter(tickets, m_SearchTerms, rowFilter -> {
            m_RowFilterJustUpdated = true;
            m_Table.getRowSorter().setRowFilter(rowFilter);
            updateMatches();
        });
    }

    private void updateMatches() {
        int rows = m_Table.getRowCount();
        m_Matches.setText(rows == 0 ? "" : "Matches: " + rows);
    }

    private void updateViews() {
        displaySelectedTickets();
        updatePlugin();
    }

    private void displaySelectedTickets() {
        Ticket[] selected = getSelectedTickets();

        String text = HtmlFormatter.buildDescription(selected, m_SearchTerms);
        m_DescriptionPane.updateDescription(text);

        // Avoid updating downloads via this very simple check. (Could make more
        // sophisticated)
        if (!Arrays.equals(selected, m_DisplayedTickets)) {
            m_Downloads.count(selected);
        }
        m_DisplayedTickets = Arrays.copyOf(selected, selected.length);
        if (selected.length > 0) {
            scrollToTicket(selected[0].getNumber());
        }
    }

    private void scrollToTicket(int ticketNumber) {
        int newRow = findViewRowForTicket(ticketNumber);
        if (newRow != -1) {
            SwingUtilities.invokeLater(() ->
                m_Table.scrollRectToVisible(m_Table.getCellRect(newRow, -1, true)));
        }
    }

    private void updatePlugin() {
        if (m_ActivePlugin != null) {
            m_ActivePlugin.ticketViewUpdated(getViewedTickets(), getSelectedTickets());
        }
    }

    /** -1 for not found */
    private int findViewRowForTicket(int ticketNumber) {
        TicketTableModel model = m_Table.getModel();
        int rowCount = m_Table.getRowCount();
        for (int r = 0; r < rowCount; r++) {
            int modelRow = m_Table.convertRowIndexToModel(r);
            if (model.getTicket(modelRow).getNumber() == ticketNumber) {
                return r;
            }
        }
        return -1;
    }

    private Ticket[] getViewedTickets() {
        TicketTableModel model = m_Table.getModel();
        Ticket[] tickets = new Ticket[m_Table.getRowCount()];
        for (int i = 0; i < tickets.length; i++) {
            int row = m_Table.convertRowIndexToModel(i);
            tickets[i] = model.getTicket(row);
        }
        return tickets;
    }

    private Ticket[] getSelectedTickets() {
        int[] rows = m_Table.getSelectedRows();
        TicketTableModel model = m_Table.getModel();

        Ticket[] tickets = new Ticket[rows.length];
        for (int i = 0; i < rows.length; i++) {
            int row = m_Table.convertRowIndexToModel(rows[i]);
            tickets[i] = model.getTicket(row);
        }
        return tickets;
    }

    @Override
    public void dispose() {
        new FrameStatePersister(FRAME_STATE_PROPERTY, this).saveFrameState();
        switchPlugin(null);
        m_FilterComputor.shutdown();
        m_FilterCombo.saveSearches();
        super.dispose();
    }

    public void installToolPanel(ToolPlugin plugin) {
        m_Plugins.put(plugin, plugin.initialise(m_TableModelUpdater));
        m_PluginCombo.addItem(plugin);
        GuiUtilities.makeMaxASmidgeWider(m_PluginCombo, GAP);
    }

    public TicketTableModel getTicketModel() {
        return m_Table.getModel();
    }

    public SlurpAction getSlurpAction() {
        return slurpAction;
    }
}
