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

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

class PeriodStatistics {

    public static final long MEDIUM_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    public static final long LONG_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(400);

    public enum Category {
        SHORT(MEDIUM_THRESHOLD_NANOS),
        MEDIUM(LONG_THRESHOLD_NANOS),
        LONG(Long.MAX_VALUE);

        public final long threshold;
        private Category(long thresh) {
            threshold = thresh;
        }
        public static Category fromNanos(long nanos) {
            for (Category category : Category.values()) {
                if (nanos <= category.threshold) {
                    return category;
                }
            }
            throw new AssertionError("Impossible");
        }
    }

    public static final class CategoryStats {
        public int count;
        public long nanos;

        public CategoryStats() {
        }
        public CategoryStats(CategoryStats other) {
            count = other.count;
            nanos = other.nanos;
        }
        public void accumulate(long additionalNanos) {
            ++ count;
            nanos += additionalNanos;
        }
    }

    public long reportingIntervalNanos;
    public EnumMap<Category, CategoryStats> statsMap = initStatsMap();
    public long incompleteNanos = 0L;
    public long longestEvent = 0L;
    public StackTraceElement[] stack;

    private static EnumMap<Category, CategoryStats> initStatsMap() {
        EnumMap<Category, CategoryStats> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, new CategoryStats());
        }
        return map;
    }

    private static EnumMap<Category, CategoryStats> copy(Map<Category, CategoryStats> other) {
        EnumMap<Category, CategoryStats> map = new EnumMap<>(Category.class);
        for (Entry<Category, CategoryStats> entry : other.entrySet()) {
            map.put(entry.getKey(), new CategoryStats(entry.getValue()));
        }
        return map;
    }

    public PeriodStatistics(long reportingIntervalMs) {
        this.reportingIntervalNanos = TimeUnit.MILLISECONDS.toNanos(reportingIntervalMs);
    }

    public PeriodStatistics(PeriodStatistics other) {
        reportingIntervalNanos = other.reportingIntervalNanos;
        incompleteNanos = other.incompleteNanos;
        statsMap = copy(other.statsMap);
        longestEvent = other.longestEvent;
        stack = other.stack;
    }

    public CategoryStats getShort() {
        return statsMap.get(Category.SHORT);
    }

    public CategoryStats getMedium() {
        return statsMap.get(Category.MEDIUM);
    }

    public CategoryStats getLong() {
        return statsMap.get(Category.LONG);
    }

    public void recordElapsedNanos(long elapsed) {
        accumulate(elapsed, Category.fromNanos(elapsed));
    }

    public void completeFinalEvent(long additionalNanos) {
        accumulate(incompleteNanos, Category.fromNanos(incompleteNanos + additionalNanos));
        incompleteNanos = 0L;
    }

    public void merge(PeriodStatistics other) {
        reportingIntervalNanos += other.reportingIntervalNanos;
        incompleteNanos += other.incompleteNanos;
        for (Entry<Category, CategoryStats> entry : other.statsMap.entrySet()) {
            Category key = entry.getKey();
            CategoryStats value = entry.getValue();
            CategoryStats stat = statsMap.get(key);
            stat.count += value.count;
            stat.nanos += value.nanos;
        }
        if (longestEvent < other.longestEvent) {
            longestEvent = other.longestEvent;
        }
    }

    private void accumulate(long nanos, Category category) {
        statsMap.get(category).accumulate(nanos);
        if (longestEvent < nanos) {
            longestEvent = nanos;
        }
    }

    /**
     * Called when the time period after us ends with an EDT event still ongoing the whole way
     * through. When that happens, it's time to flag our "uncharged time" as long (and move on).
     */
    public void accrueIncomplete() {
        statsMap.get(Category.LONG).accumulate(incompleteNanos);
        incompleteNanos = 0L;
    }

    public float percentBusy() {
        return ((float) totalBusyNanos()) / reportingIntervalNanos * 100f;
    }

    private long totalBusyNanos() {
        long sum = 0L;
        for (CategoryStats stats : statsMap.values()) {
            sum += stats.nanos;
        }
        return sum;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(String.format(
                "Busy for %d / %d ms (%.1f%%), Longest: %d ms\n" +
                "  %5d short:  %5d ms\n" +
                "  %5d medium: %5d ms\n" +
                "  %5d long:   %5d ms",
                TimeUnit.NANOSECONDS.toMillis(totalBusyNanos()),
                TimeUnit.NANOSECONDS.toMillis(reportingIntervalNanos),
                percentBusy(),
                TimeUnit.NANOSECONDS.toMillis(longestEvent),
                statsMap.get(Category.SHORT).count,
                TimeUnit.NANOSECONDS.toMillis(statsMap.get(Category.SHORT).nanos),
                statsMap.get(Category.MEDIUM).count,
                TimeUnit.NANOSECONDS.toMillis(statsMap.get(Category.MEDIUM).nanos),
                statsMap.get(Category.LONG).count,
                TimeUnit.NANOSECONDS.toMillis(statsMap.get(Category.LONG).nanos)));
        if (stack != null) {
            result.append("\nAn example call-stack during the busy period:\n");
            for (StackTraceElement element : stack) {
                result.append("\tat " + element + "\n");
            }
        }
        return result.toString();
    }
}
