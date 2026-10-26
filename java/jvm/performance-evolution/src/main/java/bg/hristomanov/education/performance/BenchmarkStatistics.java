package bg.hristomanov.education.performance;

import java.util.List;

/**
 * Use medians of repeated identical process launches, never a single lucky run.
 * These are wall-clock process durations, NOT isolated JVM startup or throughput.
 */
public final class BenchmarkStatistics {

    private BenchmarkStatistics() {
    }

    public static double medianMillis(List<Long> durationsNanos) {
        if (durationsNanos == null || durationsNanos.isEmpty()) {
            throw new IllegalArgumentException("At least one duration is required");
        }
        List<Long> values = durationsNanos.stream().sorted().toList();
        int mid = values.size() / 2;
        if (values.size() % 2 != 0) {
            return values.get(mid) / 1_000_000.0;
        }
        // Use double addition to prevent overflowing a long.
        return ((double) values.get(mid - 1) + values.get(mid)) / 2_000_000.0;
    }
}
