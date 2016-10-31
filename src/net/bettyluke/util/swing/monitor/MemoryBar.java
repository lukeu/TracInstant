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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;

import com.sun.management.GcInfo;

public class MemoryBar extends JProgressBar {

    private static final Color NEUTRAL_HIGHLIGHT = new Color(148,89,65);
    private static final Color NEUTRAL = darken(NEUTRAL_HIGHLIGHT, 85);
    private static final Color NEUTRAL_DARKER = darken(NEUTRAL_HIGHLIGHT, 48);

    public static JPanel createPanelWithGcButton() {
        return createPanelWithGcButton(new Options(), createDefaultGCButton());
    }

    public static JPanel createPanelWithGcButton(Options options, JButton button) {
        BorderLayout layout = new BorderLayout();
        layout.setHgap(2);
        JPanel panel = new JPanel(layout);
        panel.add(new MemoryBar(options));
        panel.add(BorderLayout.EAST, button);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 5, 5, 5));
        return panel;
    }

    private static JButton createDefaultGCButton() {
        JButton button = new JButton("GC");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.gc();
            }
        });
        return button;
    }

    private static Color darken(Color c, int percentageOfOriginal) {
        return new Color(
                c.getRed() * percentageOfOriginal / 100,
                c.getGreen() * percentageOfOriginal / 100,
                c.getBlue() * percentageOfOriginal / 100);
    }

    public static class Options {
        public final int elevatedThreshold;
        public final int highThreshold;
        public final int updateIntevalMs;
        public Options() {
            this(60, 80, 500);
        }
        public Options(int elevated, int high, int updateInterval) {
            if (elevated > high) {
                throw new IllegalArgumentException("high threshold must be >= elevated");
            }
            this.elevatedThreshold = elevated;
            this.highThreshold = high;
            this.updateIntevalMs = updateInterval;
        }
    }

    private static class CollectorStats {
        long endTimeMs;
        long used = 0L;
        long max = 0L;

        @SuppressWarnings("restriction")
        CollectorStats(GcInfo info) {
            endTimeMs = info.getEndTime();
            for (Entry<String, MemoryUsage> entry : info.getMemoryUsageAfterGc().entrySet()) {
                String pool = entry.getKey().toUpperCase();
                if (pool.contains("PERM") || pool.contains("METASPACE") || pool.contains("CACHE")) {
                    continue;
                }
                MemoryUsage usage = entry.getValue();
                used += usage.getUsed();
                max += usage.getMax();
            }
        }
    }

    private final Options options;
    private final Map<String, CollectorStats> recentCollections =
            new TreeMap<String, CollectorStats>();
    private Timer timer;

    private CollectorStats memoryEstimate; // Lower estimate, at deep collection
    private long upperEstimate; // MIN (smallest shallow collection, current use)
    private long usedBytes;
    private long reservedBytes;
    private long maxBytes;

    public MemoryBar(Options opt) {
        this.options = opt;
        addHierarchyListener(new HierarchyListener() {

            @Override
            public void hierarchyChanged(HierarchyEvent he) {
                if ((he.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (timer != null) {
                        timer.stop();
                    }
                    timer = isShowing() ? createStartedTimer() : null;
                }
            }

            private Timer createStartedTimer() {
                Timer result = new Timer(options.updateIntevalMs, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        updateStatistics();
                    }
                });
                result.setRepeats(true);
                result.setCoalesce(true);
                result.start();
                return result;
            }
        });
        updateStatistics();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics g2 = g.create();
        int w = getWidth();
        int h = getHeight();
        int x;


        x = (int) (reservedBytes * w / maxBytes);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, x, h);
        g2.setColor(getBackground());
        g2.fillRect(x, 0, w-x, h);

        g2.setColor(getMainBarColour());
        int prevX = x = (int) (getMemoryEstimate() * w / maxBytes);
        g2.fillRect(0, 0, x, h);

        g2.setColor(getDarkBarColour());
        x = (int) (upperEstimate * w / maxBytes);
        g2.fillRect(prevX, 0, x - prevX, h);

        x = (int) (usedBytes * w / maxBytes);
        g2.setColor(getHighlightBarColour());
        g2.fillRect(prevX-1, 0, 2, h);
        g2.setColor(getMainBarColour());
        g2.fillRect(x-1, 0, 2, h);

        for (CollectorStats stats : recentCollections.values()) {
            if (stats != memoryEstimate) {
                x = (int) (stats.used * w / maxBytes);
                g2.drawLine(x, 0, x, h);
            }
        }
        BorderFactory.createLoweredBevelBorder().paintBorder(this, g2, 0, 0, w, h);
        g2.dispose();
    }

    private Color getHighlightBarColour() {
        int est = (int) (getMemoryEstimate() / maxBytes);
        if (est > options.highThreshold) {
            return Color.RED;
        }
        if (est > options.elevatedThreshold) {
            return Color.ORANGE;
        }
        return NEUTRAL_HIGHLIGHT;
    }

    private Color getDarkBarColour() {
        return NEUTRAL_DARKER;
    }

    private Color getMainBarColour() {
        return NEUTRAL;
    }

    private long getMemoryEstimate() {
        return memoryEstimate == null ? usedBytes : memoryEstimate.used;
    }

    @SuppressWarnings("restriction")
    public void updateStatistics() {

        memoryEstimate = null;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc instanceof com.sun.management.GarbageCollectorMXBean) {
                storeCollectorStats((com.sun.management.GarbageCollectorMXBean) gc);
            }
        }

        Runtime runtime = Runtime.getRuntime();
        reservedBytes = runtime.totalMemory();
        usedBytes = reservedBytes - runtime.freeMemory();
        maxBytes = runtime.maxMemory();

        long last = memoryEstimate.endTimeMs;
        upperEstimate = memoryEstimate.used;
        for (CollectorStats stats : recentCollections.values()) {
            if (stats != memoryEstimate && stats.endTimeMs > last) {
                last = stats.endTimeMs;
                upperEstimate = Math.min(usedBytes, stats.used);
            }
        }

        setMinimum(0);
        setMaximum(100);
        setValue(50);
        repaint();
    }

    @SuppressWarnings("restriction")
    private void storeCollectorStats(com.sun.management.GarbageCollectorMXBean gc) {
        GcInfo info = gc.getLastGcInfo();
        if (info != null) {
            CollectorStats collectorStats = new CollectorStats(info);
            recentCollections.put(gc.getName(), collectorStats);

            if (memoryEstimate == null || collectorStats.used < getMemoryEstimate()) {
                memoryEstimate = collectorStats;
            }
        }
    }

    @SuppressWarnings("restriction")
    private static void printGCStats() {
        long totalGarbageCollections = 0;
        long garbageCollectionTime = 0;

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {

            long count = gc.getCollectionCount();

            if(count >= 0) {
                totalGarbageCollections += count;
            }

            long time = gc.getCollectionTime();

            if(time >= 0) {
                garbageCollectionTime += time;
            }

            if (gc instanceof com.sun.management.GarbageCollectorMXBean) {
                com.sun.management.GarbageCollectorMXBean sunGC =
                        (com.sun.management.GarbageCollectorMXBean) gc;

                System.out.print(gc.getName());

                GcInfo info = sunGC.getLastGcInfo();
                if (info != null) {

//                    System.out.println(" (" + info.getId()+ ") took " + info.getDuration() + " milliseconds; start-end times " + info.getStartTime()+ "-" + info.getEndTime());
//                    System.out.println("GcInfo CompositeType: " + info.getCompositeType());
//                    System.out.println("GcInfo MemoryUsageAfterGc: \n" + formatUsage(info.getMemoryUsageAfterGc()));
//                    System.out.println("GcInfo MemoryUsageBeforeGc: \n" + formatUsage(info.getMemoryUsageBeforeGc()));
                }
            }
        }

//        System.out.println("Total Garbage Collections: " + totalGarbageCollections);
//        System.out.println("Total Garbage Collection Time (ms): " + garbageCollectionTime);
    }

    private static String formatUsage(Map<String, MemoryUsage> usages) {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, MemoryUsage> entry : usages.entrySet()) {
            sb.append("  " + entry.getKey() + " => " + entry.getValue() + "\n");
        }
        return sb.toString();
    }

    // Collect memory used after each of the recorded (recent) GC events.
    //  - Report the lowest as the 'estimate' of memory usage. (Value and time.)
    //  - Render all other 'low water marks' in the Bar, along with current

}
