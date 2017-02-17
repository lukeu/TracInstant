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

import java.util.EventListener;

import javax.swing.event.EventListenerList;

public class EdtMonitorModel {

    public static interface StatListener extends EventListener{
        public void statsUpdate(int updateCount);
    }

    static final int NUMBER_OF_BINS = 100;

    public final PeriodStatistics[] statBins = new PeriodStatistics[NUMBER_OF_BINS];
    private int binWriteIndex = 0;

    private final EventListenerList listenerList = new EventListenerList();

    protected int updateIndex;

    public void push(PeriodStatistics stats) {
        statBins[getAndIncrementWriteIndex()] = stats;
    }

    /**
     * @param index 0 is the most recent statistics. Returns null for indexes in the ring buffer
     * which have not been accumulated yet.
     */
    public PeriodStatistics getBin(int index) {
        index = binWriteIndex - index - 1;
        while (index < 0) {
            index += statBins.length;
        }
        return statBins[index];
    }

    PeriodStatistics getPreviousBin() {
        int index = ((binWriteIndex == 0) ? statBins.length : binWriteIndex) - 1;
        return statBins[index];
    }

    int getAndIncrementWriteIndex() {
        int result = (binWriteIndex++);
        if (binWriteIndex == statBins.length) {
            binWriteIndex = 0;
        }
        return result;
    }

    public void addStatListener(StatListener listener) {
        listenerList.add(StatListener.class, listener);
    }

    public void removeStatListener(StatListener listener) {
        listenerList.remove(StatListener.class, listener);
    }

    public void fireStatEvent(int updateCount) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == StatListener.class) {
                ((StatListener) listeners[i + 1]).statsUpdate(updateCount);
            }
        }
    }

    public void setDataFrom(EdtMonitorModel other) {
        for (int i = 0, count = other.statBins.length; i < count; i++) {
            PeriodStatistics bin = other.statBins[i];
            statBins[i] = (bin == null) ? null : new PeriodStatistics(bin);
        }
        binWriteIndex = other.binWriteIndex;
    }
}
