package bg.hristomanov.education.performance.hardwood;

import java.util.Objects;

/**
 * A conservative metadata-only teaching model inspired by Parquet page pruning.
 * This does NOT parse Parquet metadata, pages, null-level streams or Bloom filters.
 *
 * Decisions are sound only when statistics and optional Bloom evidence are
 * trustworthy and use the same ordering/semantics as the actual values.
 */
public final class PagePruningPolicy {

    private PagePruningPolicy() {
    }

    public enum Decision {
        CANNOT_MATCH,
        MIGHT_MATCH,
        ALWAYS_MATCHES
    }

    /**
     * A missing null count or min/max value means UNKNOWN, never zero.
     * Minimum and maximum apply only to present, non-null values.
     */
    public record PageStatistics(long valueCount, Long knownNullCount,
                                 Long minimum, Long maximum) {

        public PageStatistics {
            if (valueCount < 0) {
                throw new IllegalArgumentException("valueCount cannot be negative");
            }
            if (knownNullCount != null
                    && (knownNullCount < 0 || knownNullCount > valueCount)) {
                throw new IllegalArgumentException("Invalid null count");
            }
            if (minimum != null && maximum != null && minimum > maximum) {
                throw new IllegalArgumentException("min must not exceed max");
            }
        }

        public boolean hasNoNulls() {
            return knownNullCount != null && knownNullCount == 0;
        }

        public boolean isKnownEmptyOrAllNull() {
            return valueCount == 0
                    || knownNullCount != null && knownNullCount == valueCount;
        }
    }

    /**
     * A real Parquet Bloom filter may yield false positives, but not false
     * negatives. A false return must therefore be PROOF of absence.
     * This is an evidence interface, not a Bloom-filter implementation.
     */
    @FunctionalInterface
    public interface BloomEvidence {
        boolean mightContain(long value);
    }

    public static Decision greaterThan(PageStatistics statistics, long threshold) {
        Objects.requireNonNull(statistics, "statistics");
        if (statistics.isKnownEmptyOrAllNull()) {
            return Decision.CANNOT_MATCH;
        }
        if (statistics.maximum() != null && statistics.maximum() <= threshold) {
            return Decision.CANNOT_MATCH;
        }
        if (statistics.minimum() != null
                && statistics.minimum() > threshold
                && statistics.hasNoNulls()) {
            return Decision.ALWAYS_MATCHES;
        }
        return Decision.MIGHT_MATCH;
    }

    public static Decision equalTo(PageStatistics statistics, long target,
                                   BloomEvidence bloomEvidence) {
        Objects.requireNonNull(statistics, "statistics");
        if (statistics.isKnownEmptyOrAllNull()) {
            return Decision.CANNOT_MATCH;
        }
        if (statistics.minimum() != null && target < statistics.minimum()) {
            return Decision.CANNOT_MATCH;
        }
        if (statistics.maximum() != null && target > statistics.maximum()) {
            return Decision.CANNOT_MATCH;
        }
        if (bloomEvidence != null && !bloomEvidence.mightContain(target)) {
            return Decision.CANNOT_MATCH;
        }
        if (statistics.minimum() != null && statistics.maximum() != null
                && statistics.minimum() == target
                && statistics.maximum() == target
                && statistics.hasNoNulls()) {
            return Decision.ALWAYS_MATCHES;
        }
        return Decision.MIGHT_MATCH;
    }
}
