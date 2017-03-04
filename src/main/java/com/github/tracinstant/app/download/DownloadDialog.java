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
package com.github.tracinstant.app.download;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.JTextComponent;

import com.github.swingdpi.util.ScaledTable;
import com.github.tracinstant.app.download.DownloadModel.ListModelView;
import com.github.tracinstant.app.prefs.TracInstantProperties;
import com.github.tracinstant.app.ui.BrowsePanel;
import com.github.tracinstant.app.ui.TracInstantFrame;
import com.github.tracinstant.util.DocUtils;

// TODO: Enforce single-instance creation - since this is the only anticipated usage
// we are lazy and don't disconnect listeners.
public class DownloadDialog extends JDialog {

    private static final String[] COLUMNS = { "", "File", "Status" };
    private static final int[] DEFAULT_COL_WIDTH = { 1000, 30000, 10000 };

    public class TargetTableModel extends AbstractTableModel {
        private ListModelView listModel;

        public TargetTableModel(ListModelView listModel) {
            this.listModel = listModel;
            listModel.addListDataListener(new ListDataListener() {
                @Override
                public void intervalRemoved(ListDataEvent e) {
                    fireTableRowsDeleted(e.getIndex0(), e.getIndex1());
                }

                @Override
                public void intervalAdded(ListDataEvent e) {
                    fireTableRowsInserted(e.getIndex0(), e.getIndex1());
                }

                @Override
                public void contentsChanged(ListDataEvent e) {
                    fireTableRowsUpdated(e.getIndex0(), e.getIndex1());
                }
            });
        }

        @Override
        public int getRowCount() {
            return listModel.getSize();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Target target = listModel.getElementAt(rowIndex);
            Path file = downloadModel.getAbsolutePath(target);
            if ("".equals(COLUMNS[columnIndex])) {
                return target.isSelected();
            } else if ("File".equals(COLUMNS[columnIndex])) {
                return file;
            } else if ("Status".equals(COLUMNS[columnIndex])) {
                switch (target.getState()) {
                case IDLE:
                    if (target.isOverwriting()) {
                        return "FILE EXISTS";
                    }
                    return "";
                case STARTED:
                    return "Downloading"; // TODO: add percentage ?
                case ENDED:
                    return "Finished";
                case ERROR:
                    return "An error occurred";
                default:
                    break;
                }
            }
            throw new AssertionError();
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Target target = listModel.getElementAt(rowIndex);
            if ("".equals(COLUMNS[columnIndex])) {
                target.setSelected((Boolean) aValue);
                listModel.modifiedElementAt(rowIndex);
                updateControls();
            }
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if ("".equals(COLUMNS[columnIndex])) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }
    }

    class StartDownloadAction extends AbstractAction {

        public StartDownloadAction() {
            super("Start downloading");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File bugsFolder = browsePanel.getPath();
            TracInstantProperties.get().putFilePath("LocalBugsDir", bugsFolder);
            if (!bugsFolder.exists()) {
                bugsFolder.mkdirs();
            }
            if (!bugsFolder.isDirectory()) {
                showErrorMessage("Directory not found: " + bugsFolder);
                return;
            }

            // TODO: split actions so that setting the bugs folder updates the download
            // list immediately, and the OK button is disabled unless the
            // folder exists (perhaps with a separate "create this folder" option).

            downloadModel.setBugsFolder(bugsFolder);
            downloadModel.download();
        }

        private void showErrorMessage(String message) {
            JOptionPane.showMessageDialog(
                DownloadDialog.this, message, getTitle(), JOptionPane.ERROR_MESSAGE);
        }
    }

    class StopDownloadAction extends AbstractAction {

        public StopDownloadAction() {
            super("Stop downloading");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            downloadModel.cancelDownload();
        }
    }

    class HideAction extends AbstractAction {

        public HideAction() {
            super("Hide");
        }

        @Override
        public void actionPerformed(ActionEvent e) {

            // This is intended as a singleton dialog
            setVisible(false);
        }
    }

    private static final int GAP = 8;
    private final DownloadModel downloadModel;
    private final BrowsePanel browsePanel;
    private final JComponent tablePanel;
    private final JTextComponent statusPanel;

    private final Action startAction = new StartDownloadAction();
    private final Action stopAction = new StopDownloadAction();
    private final Action hideAction = new HideAction();

    JButton startButton = new JButton(startAction);
    JButton stopButton = new JButton(stopAction);

    public DownloadDialog(TracInstantFrame parent, DownloadModel model) {
        super(parent, "Download attachments", ModalityType.MODELESS);
        this.downloadModel = model;
        this.browsePanel = createBrowsePanel();
        this.tablePanel = createTablePanel();
        this.statusPanel = createFeedbackPanel();

        JPanel mainPanel = newBorderedPanel(browsePanel, tablePanel, statusPanel);
        getContentPane().add(newBorderedPanel(null, mainPanel, createButtonRow()));

        updateControls();
        addKeyboardShortcuts();
        addBehaviour();

        mainPanel.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, 0, GAP));
        setSize(640, 500);
        setLocationRelativeTo(parent);
    }

    private void addKeyboardShortcuts() {
        getRootPane().setDefaultButton(startButton);
        getRootPane().registerKeyboardAction(hideAction,
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void addBehaviour() {
        downloadModel.addChangeListener(e -> updateControls());

        JTextComponent ed = browsePanel.getLocationEditor();
        ed.getDocument().addDocumentListener(
                DocUtils.newOnAnyEventListener(
                        () -> updateBugsFolder(ed.getText())));
    }

    private void updateBugsFolder(String filename) {
        downloadModel.setBugsFolder(new File(filename));
    }

    protected void updateControls() {
        updateButtonEnabledStates();
        statusPanel.setText(getStatusText());
    }

    private String getStatusText() {
        switch (downloadModel.getState()) {
        case CANCELLING:
            return "Cancelling";
        case COUNTING:
            return "Finding attachments...";
        case DOWNLOADING:
            return downloadModel.getDownloadSummary();
        case IDLE:
            break;
        }
        if (downloadModel.countComplete() > 0 && downloadModel.countSelected() == 0) {
            return "Download complete: " +
                downloadModel.countComplete() + " / " +
                downloadModel.countFilesToDownloadOrDownloaded() +
                "  (Tip: double-click to explore to a file)";
        }

        String s = downloadModel.countSelected() + " files selected.";
        int toOverwrite = downloadModel.countFilesToOverwrite();
        if (toOverwrite > 0) {
            s += " " + toOverwrite + " will be overwritten!";
        }
        if (!Files.exists(downloadModel.getBugsFolder())) {
            s += " The download folder will be created.";
        }
        return s;
    }

    private void updateButtonEnabledStates() {
        boolean startWasEnabled = startAction.isEnabled();
        startAction.setEnabled(!downloadModel.isBusy() && downloadModel.getNumDownloads() > 0);
        stopAction.setEnabled(downloadModel.isDownloading());

        if (startWasEnabled && !startAction.isEnabled()) {
            if (stopAction.isEnabled()) {
                stopButton.requestFocusInWindow();
            }
        } else if (stopButton.hasFocus() && !stopAction.isEnabled()) {
            if (startAction.isEnabled()) {
                startButton.requestFocusInWindow();
            }
        }
    }

    private BrowsePanel createBrowsePanel() {
        return new BrowsePanel(downloadModel.getBugsFolder().toFile());
    }

    private JComponent createTablePanel() {
        TargetTableModel tableModel = new TargetTableModel(downloadModel.getListModel());
        JTable table = new ScaledTable(tableModel);
        int i = 0;
        for (int w : DEFAULT_COL_WIDTH) {
            table.getColumnModel().getColumn(i++).setPreferredWidth(w);
        }

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 2 &&
                        1 == table.getSelectedRowCount()) {
                    browseToFolderOfSelectedRow(table);
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
                if (evt.getKeyCode() == KeyEvent.VK_ENTER && 1 == table.getSelectedRowCount()) {
                    browseToFolderOfSelectedRow(table);
                    evt.consume();
                }
                if (evt.getKeyCode() == KeyEvent.VK_SPACE && table.getSelectedRowCount() > 0) {
                    toggleSelected();
                    evt.consume();
                }
            }

            // Toggle the checkbox on all selected rows
            private void toggleSelected() {
                boolean selected = (Boolean) table.getModel().getValueAt(table.getSelectedRow(), 0);
                for (int viewRow : table.getSelectedRows()) {
                    int row = table.convertRowIndexToModel(viewRow);
                    table.getModel().setValueAt(!selected, row, 0);
                }
            }
        });
        return new JScrollPane(table);
    }

    protected void browseToFolderOfSelectedRow(JTable table) {
        int tableViewRow = table.getSelectedRow();
        int tableModelRow = table.convertRowIndexToModel(tableViewRow);
        browseToTargetFolder(downloadModel.getListModel().getElementAt(tableModelRow));
    }

    protected void browseToTargetFolder(Object oTarget) {
        File file = downloadModel.getAbsolutePath((Target) oTarget).toFile();
        while (file != null) {
            if (file.isDirectory()) {
                try {
                    Desktop.getDesktop().open(file);
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            } else {
                file = file.getParentFile();
            }
        }
    }

    private JTextComponent createFeedbackPanel() {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setText("&nbsp;");
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        pane.putClientProperty("Nimbus.Overrides.InheritDefaults", true);
        UIDefaults defaults = new UIDefaults();
        Color bg = new Color(getBackground().getRGB());
        defaults.put("EditorPane[Enabled].backgroundPainter", bg);
        pane.putClientProperty("Nimbus.Overrides", defaults);
        pane.setBackground(bg);
        pane.setOpaque(false);
        pane.setFocusable(false);
        pane.setEditable(false);
        Insets in = pane.getInsets();
        pane.setBorder(BorderFactory.createEmptyBorder(in.top, in.left, in.bottom, in.right));
        return pane;
    }

    private static JPanel newBorderedPanel(Component north, Component centre, Component south) {
        BorderLayout layout = new BorderLayout();
        layout.setVgap(GAP);
        JPanel panel = new JPanel(layout);
        panel.add(centre);
        if (north != null) {
            panel.add(north, BorderLayout.NORTH);
        }
        if (south != null) {
            panel.add(south, BorderLayout.SOUTH);
        }

        return panel;
    }

    private Box createButtonRow() {
        Box buttonRow = Box.createHorizontalBox();
        buttonRow.add(new JButton(hideAction));
        buttonRow.add(Box.createHorizontalGlue());
        buttonRow.add(startButton);
        buttonRow.add(Box.createHorizontalStrut(6));
        buttonRow.add(stopButton);
        buttonRow.setBorder(BorderFactory.createEmptyBorder(0, GAP, GAP, GAP));
        return buttonRow;
    }
}
