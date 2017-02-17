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

package net.bettyluke.tracinstant.ui;

import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.JDialog;

import net.bettyluke.util.swing.monitor.EdtMonitor;
import net.bettyluke.util.swing.monitor.EdtMonitorPanel;

public class ShowPerformanceMonitorAction extends AbstractAction {

    private Window dialogParent;

    public ShowPerformanceMonitorAction(Window parent) {
        super("perf-mon");
        dialogParent = parent;
    }

    private EdtMonitor monitor = null;

    @Override
    public void actionPerformed(ActionEvent e) {
        EdtMonitorPanel panel = new EdtMonitorPanel(getMonitor().getDataModel());

        JDialog dialog = new JDialog(dialogParent, "Performance monitor", ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.add(panel);
        dialog.setSize(new Dimension(640, 480));
        dialog.setLocationRelativeTo(dialogParent);
        dialog.setVisible(true);
    }

    private EdtMonitor getMonitor() {
        if (monitor == null) {
            monitor = new EdtMonitor();
            monitor.startMonitoring();

            // Ensure the EDT monitor shuts down when the main frame closes, otherwise the AWT
            // auto-shutdown won't work & we'll be left with a zombie process.
            dialogParent.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent evt) {
                    monitor.stopMonitoring();
                }

                @Override
                public void windowClosing(WindowEvent evt) {
                    monitor.stopMonitoring();
                }
            });

        }
        return monitor;
    }
}
