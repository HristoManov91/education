package bg.hristomanov.education.performance.hardwood;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Parquet-inspired page scheduling exercise, NOT a Parquet reader or benchmark.
 *
 * Virtual threads represent blocking page fetches; a fixed platform-thread
 * pool bounds CPU decodes. A sliding window bounds the number of scheduled
 * pages (not total bytes). Results are returned in original page order.
 *
 * This deliberately does NOT claim to implement Hardwood's adaptive
 * per-column prefetching, vectorization or memory accounting.
 */
public final class PagePipelineLab {

    private PagePipelineLab() {
    }

    public record PageRef(int rowGroup, String column, int pageIndex) {
        public PageRef {
            if (rowGroup < 0 || pageIndex < 0) {
                throw new IllegalArgumentException("Page coordinates must be nonnegative");
            }
            if (column == null || column.isBlank()) {
                throw new IllegalArgumentException("Column is required");
            }
        }
    }

    public record PageResult(PageRef page, long decodedValue) {
        public PageResult {
            Objects.requireNonNull(page, "page");
        }
    }

    /**
     * maxOutstandingPages is based on submitted futures, not bytes or heap.
     */
    public record RunReport(List<PageResult> results, int maxOutstandingPages,
                            int cpuWorkerLimit) {
        public RunReport {
            results = List.copyOf(Objects.requireNonNull(results, "results"));
            if (maxOutstandingPages < 0 || cpuWorkerLimit < 1) {
                throw new IllegalArgumentException("Invalid scheduler metrics");
            }
        }
    }

    @FunctionalInterface
    public interface PageLoader {
        byte[] load(PageRef page) throws Exception;
    }

    @FunctionalInterface
    public interface PageDecoder {
        long decode(PageRef page, byte[] encoded) throws Exception;
    }

    private record Pending(int position, Future<PageResult> future) {
    }

    public static RunReport run(List<PageRef> pages, int maxInFlight,
                                int cpuWorkers, PageLoader loader,
                                PageDecoder decoder)
            throws ExecutionException, InterruptedException {
        Objects.requireNonNull(pages, "pages");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(decoder, "decoder");
        if (maxInFlight <= 0 || cpuWorkers <= 0) {
            throw new IllegalArgumentException("Worker counts must be positive");
        }

        List<PageRef> input = List.copyOf(pages);
        if (input.isEmpty()) {
            return new RunReport(List.of(), 0, cpuWorkers);
        }

        try (ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
             ExecutorService cpu = Executors.newFixedThreadPool(cpuWorkers)) {
            List<PageResult> ordered = new ArrayList<>(
                    Collections.nCopies(input.size(), null));
            ArrayDeque<Pending> outstanding = new ArrayDeque<>();
            int maximumOutstanding = 0;

            try {
                for (int i = 0; i < input.size(); i++) {
                    // An explicitly bounded window avoids scheduling every
                    // page when decoding or I/O is slower than production.
                    if (outstanding.size() == maxInFlight) {
                        collectOldest(outstanding, ordered);
                    }

                    PageRef page = input.get(i);
                    Future<PageResult> task = io.submit(() -> {
                        byte[] encoded = Objects.requireNonNull(
                                loader.load(page), "page payload");
                        // CPU work is NOT launched on an unlimited pool of
                        // virtual threads; it is bounded by platform workers.
                        return cpu.submit(() -> new PageResult(
                                page, decoder.decode(page, encoded))).get();
                    });
                    outstanding.addLast(new Pending(i, task));
                    maximumOutstanding = Math.max(maximumOutstanding, outstanding.size());
                }

                while (!outstanding.isEmpty()) {
                    collectOldest(outstanding, ordered);
                }
                return new RunReport(ordered, maximumOutstanding, cpuWorkers);
            } catch (ExecutionException | InterruptedException failure) {
                // Failed/cancelled work must not leave later page tasks running.
                for (Pending pending : outstanding) {
                    pending.future().cancel(true);
                }
                io.shutdownNow();
                cpu.shutdownNow();
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw failure;
            }
        }
    }

    private static void collectOldest(ArrayDeque<Pending> outstanding,
                                      List<PageResult> ordered)
            throws ExecutionException, InterruptedException {
        // Retrieve before removing so a failing task is also cancelled on
        // the exceptional path.
        Pending first = outstanding.getFirst();
        PageResult result = first.future().get();
        ordered.set(first.position(), result);
        outstanding.removeFirst();
    }
}
