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

package net.bettyluke.tracinstant.ui;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.RowFilter;
import javax.swing.SwingUtilities;

import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.tracinstant.data.TicketTableModel;

//import net.jcip.annotations.GuardedBy;

public class TableRowFilterComputer {

    private static final int MAX_BATCH_SIZE = 100;
    private static final AtomicInteger s_ThreadCreationCount = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = r -> {
        String name = "ComputeFilter-" + s_ThreadCreationCount.incrementAndGet();
        Thread thread = new Thread(null, r, name, 64000);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    };

    public interface ResultCallback {
        void filteringComplete(RowFilter<TicketTableModel, Integer> rowFilter);
    }

    private final static class BitSetRowFilter extends RowFilter<TicketTableModel, Integer> {
        BitSet m_Include;

        private BitSetRowFilter(BitSet include) {
            m_Include = include.get(0, include.size());
        }

        @Override
        public boolean include(Entry<? extends TicketTableModel, ? extends Integer> entry) {
            return m_Include.get(entry.getIdentifier());
        }
    }

    private static final class FilterBatchWorker implements Callable<Void> {

        public interface BatchCallback {
            void batchComplete(int firstRow, BitSet bits);
        }

        private final int m_FirstRowNumber;
        private final List<Ticket> m_Tickets;
        private final SearchTerm[] m_SearchTerms;
        private final BatchCallback m_BatchCallback;

        public FilterBatchWorker(int firstRowNumber, List<Ticket> tickets, SearchTerm[] searchTerms,
                BatchCallback callback) {
            m_FirstRowNumber = firstRowNumber;
            m_Tickets = new ArrayList<>(tickets);
            m_SearchTerms = searchTerms;
            m_BatchCallback = callback;
        }

        @Override
        public Void call() {
            BitSet bits = new BitSet(m_Tickets.size());

            // The time-consuming bit:
            int it = 0;
            for (Ticket ticket : m_Tickets) {
                bits.set(it++, include(ticket));
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }
            }
            m_BatchCallback.batchComplete(m_FirstRowNumber, bits);
            return null;
        }

        public boolean include(Ticket ticket) {

            NEXT: for (SearchTerm term : m_SearchTerms) {

                // Look at ALL ticket fields (not just those shown as columns).
                // TODO: (perf) keep global list of fields so that we can expand them
                // outside the "per-ticket" loop. (Just once per search term.)
                // Perspective: this would chop off about 5% of the processing time
                for (String field : expandFields(ticket, term.field)) {

                    // TODO: Handle null return here (after above changes)
                    String value = ticket.getValue(field);
                    if (term.pattern.matcher(value).find()) {
                        if (term.exclude) {
                            return false;
                        }
                        continue NEXT;
                    }
                }

                // Also search the special 'number' pseudo-field
                if ((term.field == null || "#".equals(term.field))
                        && term.pattern.matcher(Integer.toString(ticket.getNumber())).find()) {
                    if (term.exclude) {
                        return false;
                    }
                    continue;
                }

                // Pattern not found in any field? Fail.
                if (!term.exclude) {
                    return false;
                }
            }
            return true;
        }

        private Collection<String> expandFields(Ticket ticket, String fieldAbbreviation) {
            Collection<String> fields = ticket.getFieldNames();
            if (fieldAbbreviation == null) {
                return fields;
            }
            List<String> result = new ArrayList<>(fields.size());
            for (String field : fields) {
                if (field.length() >= fieldAbbreviation.length()
                        && field.substring(0, fieldAbbreviation.length())
                                .equalsIgnoreCase(fieldAbbreviation)) {
                    result.add(field);
                }
            }
            return result;
        }
    }

    private static final class BatchCompletionHandler implements FilterBatchWorker.BatchCallback {

        /** Store a future for each batch, purely to be able to cancel them. */
        private final List<Future<Void>> m_Futures = new ArrayList<>();

        // @GuardedBy("m_Lock")
        private final BitSet m_RowBitSet = new BitSet();
        private final Object m_Lock = new Object();

        /**
         * The number of tasks in progress that have not "reported back" as completed.
         */
        private AtomicInteger m_InProgress;

        private AtomicReference<ResultCallback> m_Callback;

        private final AtomicLong m_CreationTime = new AtomicLong(System.nanoTime());

        /**
         * @param callback
         *            Called after 'batchCount' batches have been processed.
         * @param batchCount
         *            The number of batches this handler will process. NB: <code>addBatch</code>
         *            must be called this many times.
         */
        public BatchCompletionHandler(ResultCallback callback, int batchCount) {
            m_Callback = new AtomicReference<>(callback);
            m_InProgress = new AtomicInteger(batchCount);
        }

        public void addBatch(Future<Void> future) {
            m_Futures.add(future);
        }

        public void cancel() {

            // Make sure that we won't publish our result, even if tasks don't
            // cancel promptly and still manage to call back.
            if (0 != m_InProgress.getAndSet(Integer.MAX_VALUE)) {
                System.out.format("Canceled after: %.2f ms\n",
                        (System.nanoTime() - m_CreationTime.get()) / 1000000f);
            }

            for (Future<Void> future : m_Futures) {
                future.cancel(true);
            }
        }

        /** Called-back by the batch worker, running on a pooled thread. */
        @Override
        public void batchComplete(int firstRow, BitSet bits) {

            BitSetRowFilter toPublish = null;

            // Update the 'master' BitSet m_RowBitSet
            synchronized (m_Lock) {
                int bit = -1;
                while ((bit = bits.nextSetBit(bit + 1)) != -1) {
                    m_RowBitSet.set(bit + firstRow);
                }

                if (m_InProgress.decrementAndGet() == 0) {
                    toPublish = new BitSetRowFilter(m_RowBitSet);
                }
            }

            if (toPublish != null) {
                publish(toPublish);
            }
        }

        /** NB: Running on a pooled thread. */
        private void publish(final BitSetRowFilter result) {
            SwingUtilities.invokeLater(() -> m_Callback.get().filteringComplete(result));
            System.out.format("Filter Time: %.2f ms ...  ",
                    (System.nanoTime() - m_CreationTime.get()) / 1000000f);
        }
    }

    private ThreadPoolExecutor m_Executor;
    private BatchCompletionHandler m_BatchCompletionHandler;

    public TableRowFilterComputer() {
        int threads = Runtime.getRuntime().availableProcessors();

        // If we have a high number of processor cores, keep a few cores free for
        // the GUI (or other apps) to use, to ensure maximum responsiveness. Chances are
        // that high-core CPUs are hyper-threaded, so using many more than 1/2 may have
        // diminishing (or even negative) returns on this high-memory-bandwidth task.
        if (threads > 3) {
            threads = (threads * 3) / 4;
        }
        startExecutor(threads);
    }

    /** (Re)creates m_Executor if null or the required number of threads has changed. */
    private void startExecutor(int threads) {
        if (m_Executor != null && threads != m_Executor.getMaximumPoolSize()) {
            m_Executor.shutdownNow();
            m_Executor = null;
        }
        if (m_Executor == null) {
            m_Executor = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(), THREAD_FACTORY,
                    new ThreadPoolExecutor.DiscardPolicy());
        }
    }

    public void shutdown() {
        m_Executor.shutdownNow();
    }

    static final SearchTerm[] EMPTY_SEARCH_TERMS = new SearchTerm[0];

    public void computeFilter(Ticket[] tickets, SearchTerm[] searchTerms, ResultCallback callback) {

        assert SwingUtilities.isEventDispatchThread();

        // Can use this line while debugging, for performance testing and tuning.
        // startExecutor(6);

        // Corner case.
        if (searchTerms == null || searchTerms.length == 0) {
            callback.filteringComplete(null);
            return;
        }
        if (m_BatchCompletionHandler != null) {
            m_BatchCompletionHandler.cancel();
        }
        List<Integer> batchSizes = computeBatchSizes(tickets.length);
        m_BatchCompletionHandler = new BatchCompletionHandler(callback, batchSizes.size());
        queueWorkBatches(tickets, searchTerms, batchSizes);
    }

    /**
     * @param tickets
     *            NB: Must be sorted by ID, and all IDs must be unique!
     */
    private void queueWorkBatches(Ticket[] tickets, SearchTerm[] searchTerms,
            List<Integer> batchSizes) {

        List<Ticket> batch = new ArrayList<>(MAX_BATCH_SIZE);
        int firstRowInBatch = 0;
        int it = 0;
        for (int size : batchSizes) {
            for (int i = firstRowInBatch, end = firstRowInBatch + size; i != end; ++i) {
                batch.add(tickets[it++]);
            }

            // Queue the worker task (which can start immediately)
            FilterBatchWorker worker = new FilterBatchWorker(firstRowInBatch, batch, searchTerms,
                    m_BatchCompletionHandler);
            m_BatchCompletionHandler.addBatch(m_Executor.submit(worker));
            batch.clear();
            firstRowInBatch += size;
        }
        assert firstRowInBatch == tickets.length;
    }

    private List<Integer> computeBatchSizes(final int rowCount) {

        int remaining = rowCount;

        // For what it's worth: start small, so that we can kick off background threads
        // quickly (low-latency) while we're feeding the queue.
        int size = 4;

        List<Integer> result = new ArrayList<>();
        while (remaining > 0) {
            size = Math.min(2 * size, Math.min(MAX_BATCH_SIZE, remaining));
            remaining -= size;
            result.add(size);
        }
        assert remaining == 0;
        return result;
    }
}
