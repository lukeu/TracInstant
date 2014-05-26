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

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.EmptyStackException;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

/**
 * Monitors the length of time taken to process events on the AWT's Event Dispatch Thread (EDT).
 */
public final class EdtMonitor {

    private static final long REPORTING_INTERVAL_MS = 1000;

    private static final long POLL_EDT_INTERVAL_MS = 50;

    // Although not an 'impossible' magic number, it's pretty unlikely.
    private static final long EVENT_NOT_RUNNING = -1L;

    private final class MonitoringEventQueue extends EventQueue {

        @Override
        protected void dispatchEvent(AWTEvent event) {
            try {
                before();
                super.dispatchEvent(event);
            } finally {
                after();
            }
        }

        private void before() {
            synchronized (lock) {
                long now = System.nanoTime();

                // Account for the time processed on the current event loop so far, prior to
                // descending into a new event loop.
                if (eventStartNanoTime != EVENT_NOT_RUNNING) {
                    recordElapsed(now - eventStartNanoTime);
                }
                eventStartNanoTime = now;
            }

            // Recorded for reporting the stack-trace when event processing takes too long
            eventDispatchThread = Thread.currentThread();
        }

        private void after() {
            synchronized (lock) {

                // Note that we don't measure time after dropping out of one event loop, while
                // back in the parent event loop. Doing so appears to log idle time...(??)
                long now = System.nanoTime();
                if (eventStartNanoTime != EVENT_NOT_RUNNING) {
                    recordElapsed(now - eventStartNanoTime);
                }
                eventStartNanoTime = EVENT_NOT_RUNNING;
            }
            eventDispatchThread = null;
        }

        private void recordElapsed(long elapsed) {
            currentStats.recordElapsedNanos(elapsed);
            PeriodStatistics previous = collectionModel.getPreviousBin();
            if (previous != null && previous.incompleteNanos != 0L) {
                previous.completeFinalEvent(elapsed);
            }
        }

        /**
         * Overridden to make public
         * <p>
         * {@inheritDoc}
         *
         * @throws EmptyStackException if no previous push was made on this <code>EventQueue</code>
         */
        @Override
        public void pop() {
            super.pop();
        }
    }

    private PeriodStatistics currentStats = new PeriodStatistics(REPORTING_INTERVAL_MS);

    private final Object lock = new Object();

    private Timer gatherStatsTimer = null;
    private Timer longTaskTimer = null;
    private long eventStartNanoTime = EVENT_NOT_RUNNING;

    private final RotateStatsTask rotateStatsTask = new RotateStatsTask();
    private final ReportLongEdtTaskTask reportLongEdtTaskTask = new ReportLongEdtTaskTask();

    private Thread eventDispatchThread = null;
    private int updateCounter = 0;

    /**
     * The data gathered from monitoring the EDT. All access is guarded-by 'lock'. It is updated
     * from our EVENT_QUEUE as event dispatches start & finish, and (TODO) by the long-running
     * task timer.
     */
    private final EdtMonitorModel collectionModel = new EdtMonitorModel();

    /**
     * The data model that Swing visualisations listen upon, and which is updated and fires
     * ChangeEvents shortly after the RotateTask runs. It is only ever touched upon the EDT.
     */
    private final EdtMonitorModel viewModel = new EdtMonitorModel();

    private Runnable updateViewModelRunner = new Runnable() {
        public void run() {
            int oldCount = viewModel.updateIndex;
            synchronized (lock) {
                viewModel.updateIndex = updateCounter;
                viewModel.setDataFrom(collectionModel);
            }
            viewModel.fireStatEvent(updateCounter - oldCount);
        }
    };

    private MonitoringEventQueue queue;

    private class ReportLongEdtTaskTask extends TimerTask {
        @Override
        public void run() {
            synchronized (lock) {
                if (!isProcessingEvent()) {
                    return;
                }
                if (System.nanoTime() >
                        eventStartNanoTime + PeriodStatistics.MEDIUM_THRESHOLD_NANOS) {
                    PeriodStatistics bin = currentStats;
                    if (bin.stack == null) {
                        bin.stack = eventDispatchThread.getStackTrace();
                    }
                }
            }
        }
    }

    private class RotateStatsTask extends TimerTask {
        @Override
        public void run() {
            synchronized (lock) {
                if (isProcessingEvent()) {

                    // Accumulate time into the previous bin, and reset current start time
                    long now = System.nanoTime();
                    long elapsed = now - eventStartNanoTime;
                    eventStartNanoTime = now;

                    currentStats.incompleteNanos = elapsed;

                    PeriodStatistics previous = collectionModel.getPreviousBin();
                    if (previous != null) {

                        // If there is an ongoing task (throughout the entire current bin) then
                        // bump the previous bin's "long" count before moving on. (Because when the
                        // EDT task ends, it will only increment the immediately-preceding bin.)
                        if (previous.incompleteNanos != 0L) {
                            previous.accrueIncomplete();
                        }
                    }
                } else {
                    assert currentStats.incompleteNanos == 0L;
                }
                collectionModel.push(currentStats);
                currentStats = new PeriodStatistics(REPORTING_INTERVAL_MS);
                if (Boolean.FALSE) {
                    printStats();
                }
                ++updateCounter;
            }
            SwingUtilities.invokeLater(updateViewModelRunner);
        }

        private void printStats() {
            System.out.println("\n");
            for (int i = 0, count = 10; i < count; ++i) {
                PeriodStatistics bin = collectionModel.getBin(i);
                if (bin != null) {
                    System.out.println("#" + i + ": " + bin);
                }
            }
        }
    }

    public void startMonitoring() {
        assert queue == null;
        assert gatherStatsTimer == null;
        queue = new MonitoringEventQueue();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(queue);
        long interval = REPORTING_INTERVAL_MS;
        gatherStatsTimer = new Timer("Rotate EDT statstics timer", true);
        gatherStatsTimer.scheduleAtFixedRate(rotateStatsTask, interval, interval);

        longTaskTimer = new Timer("Long-running Task Timer", true);
        longTaskTimer.scheduleAtFixedRate(
                reportLongEdtTaskTask, POLL_EDT_INTERVAL_MS, POLL_EDT_INTERVAL_MS);
    }

    public boolean isProcessingEvent() {
        return eventStartNanoTime != EVENT_NOT_RUNNING;
    }

    public void stopMonitoring() {
        if (gatherStatsTimer != null) {
            gatherStatsTimer.cancel();
            gatherStatsTimer = null;
        }
        if (longTaskTimer != null) {
            longTaskTimer.cancel();
            longTaskTimer = null;
        }
        if (queue != null) {
            queue.pop();
            queue = null;
        }
    }

    public EdtMonitorModel getDataModel() {
        return viewModel;
    }
}
