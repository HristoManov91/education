package bg.hristomanov.education.performance.hardwood;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;

/**
 * Small Java 25 teaching model inspired by Hardwood's engineering choices.
 * NOT a Parquet decoder: pages are primitive long arrays, metadata is supplied
 * by the caller, and all tests use independent row-by-row reference results.
 *
 * <p>Shows why trustworthy min/max metadata may avoid scanning values and why
 * bounded page-level CPU work needs explicit backpressure and a fixed worker
 * pool rather than one unbounded task per row.
 */
public final class PageProcessingLab {

    private PageProcessingLab() {
    }

    public enum Evidence {
        CANNOT_MATCH,
        ALL_MATCH,
        MAY_MATCH
    }

    public record Range(long fromInclusive, long toInclusive) {
        public Range {
            if (fromInclusive > toInclusive) {
                throw new IllegalArgumentException("Range minimum exceeds maximum");
            }
        }

        public boolean contains(long value) {
            return value >= fromInclusive && value <= toInclusive;
        }
    }

    /**
     * Page metadata is a trust boundary: trusted=true asserts that every
     * value is within [minimum, maximum]. The fast path intentionally does not
     * inspect all values to re-derive statistics, as that would defeat pruning.
     *
     * <p>Values are defensively copied; no mutable long[] escapes to callers.
     * Unsupported Parquet concerns (nulls, NaN, nested structures and
     * dictionary/Bloom filters) are deliberately out of this lab's scope.
     */
    public record Page(int pageIndex, long[] values, boolean trustedStatistics,
                       long minimum, long maximum) {
        public Page {
            if (pageIndex < 0) {
                throw new IllegalArgumentException("Negative page index");
            }
            Objects.requireNonNull(values, "values");
            values = values.clone();
            if (trustedStatistics && values.length > 0 && minimum > maximum) {
                throw new IllegalArgumentException("Invalid trusted statistics");
            }
        }

        @Override
        public long[] values() {
            return values.clone();
        }
    }

    public record ScanResult(long matchingRows, int decodedPages, int prunedPages,
                             int acceptedPages, int peakInFlightTasks) {
        public ScanResult {
            if (matchingRows < 0 || decodedPages < 0 || prunedPages < 0
                    || acceptedPages < 0 || peakInFlightTasks < 0) {
                throw new IllegalArgumentException("Negative scan statistics");
            }
        }
    }

    public static Evidence classify(Page page, Range range) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(range, "range");
        if (page.values.length == 0) {
            return Evidence.CANNOT_MATCH;
        }
        // Unknown/stale/corrupt metadata must NEVER be used to drop a page.
        if (!page.trustedStatistics) {
            return Evidence.MAY_MATCH;
        }
        if (page.maximum < range.fromInclusive || page.minimum > range.toInclusive) {
            return Evidence.CANNOT_MATCH;
        }
        if (page.minimum >= range.fromInclusive && page.maximum <= range.toInclusive) {
            return Evidence.ALL_MATCH;
        }
        return Evidence.MAY_MATCH;
    }

    /**
     * Independent reference oracle: do not consult page statistics, even if
     * the production-like algorithm claims every value matches.
     */
    public static ScanResult reference(List<Page> pages, Range range) {
        List<Page> immutablePages = validate(pages);
        Objects.requireNonNull(range, "range");
        long matches = 0L;
        for (Page page : immutablePages) {
            matches = Math.addExact(matches, countValues(page, range));
        }
        return new ScanResult(matches, immutablePages.size(), 0, 0, 0);
    }

    public static ScanResult sequential(List<Page> pages, Range range) {
        List<Page> immutablePages = validate(pages);
        Objects.requireNonNull(range, "range");
        long matches = 0L;
        int decoded = 0;
        int pruned = 0;
        int accepted = 0;
        for (Page page : immutablePages) {
            switch (classify(page, range)) {
                case CANNOT_MATCH -> pruned++;
                case ALL_MATCH -> {
                    accepted++;
                    matches = Math.addExact(matches, page.values.length);
                }
                case MAY_MATCH -> {
                    decoded++;
                    matches = Math.addExact(matches, countValues(page, range));
                }
            }
        }
        return new ScanResult(matches, decoded, pruned, accepted, 0);
    }

    /**
     * Process only MAY_MATCH pages on a bounded fixed platform-thread pool.
     * At most 2*workers decoding tasks may be outstanding; early pruning and
     * ALL_MATCH paths do not spend a worker thread or enqueue a task.
     *
     * <p>No timing assertions here: this demonstrates result equivalence and
     * scheduling bounds, not a performance claim for the Hardwood library.
     */
    public static ScanResult parallel(List<Page> pages, Range range, int workers)
            throws InterruptedException, ExecutionException {
        List<Page> immutablePages = validate(pages);
        Objects.requireNonNull(range, "range");
        if (workers < 1 || workers > 64) {
            throw new IllegalArgumentException("Expected between 1 and 64 CPU workers");
        }
        int maxOutstanding = workers * 2;
        long matches = 0L;
        int decoded = 0;
        int pruned = 0;
        int accepted = 0;
        int submitted = 0;
        int completed = 0;
        int peak = 0;

        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            ExecutorCompletionService<Long> completions =
                    new ExecutorCompletionService<>(pool);
            for (Page page : immutablePages) {
                switch (classify(page, range)) {
                    case CANNOT_MATCH -> pruned++;
                    case ALL_MATCH -> {
                        accepted++;
                        matches = Math.addExact(matches, page.values.length);
                    }
                    case MAY_MATCH -> {
                        if (submitted - completed == maxOutstanding) {
                            matches = Math.addExact(matches, completions.take().get());
                            completed++;
                        }
                        completions.submit(() -> countValues(page, range));
                        submitted++;
                        decoded++;
                        peak = Math.max(peak, submitted - completed);
                    }
                }
            }
            while (completed < submitted) {
                matches = Math.addExact(matches, completions.take().get());
                completed++;
            }
        }

        return new ScanResult(matches, decoded, pruned, accepted, peak);
    }

    private static List<Page> validate(List<Page> pages) {
        Objects.requireNonNull(pages, "pages");
        return List.copyOf(pages);
    }

    private static long countValues(Page page, Range range) {
        long matches = 0L;
        // Direct primitive loop avoids boxing per-row in this simplified lab.
        for (long value : page.values) {
            if (range.contains(value)) {
                matches++;
            }
        }
        return matches;
    }
}
