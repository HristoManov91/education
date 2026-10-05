package bg.hristomanov.education.algorithms.problemsolving.patterns;

/**
 * Preprocessing trade-off:
 * O(n) build веднъж → O(1) sum query много пъти.
 */
public final class PrefixSumRangeQuery {

    private final long[] prefix;

    public PrefixSumRangeQuery(int[] values) {
        prefix = new long[values.length + 1];

        for (int i = 0; i < values.length; i++) {
            prefix[i + 1] = prefix[i] + values[i];
        }
    }

    public long sumInclusive(int left, int right) {
        if (left < 0 || right < left || right + 1 >= prefix.length) {
            throw new IndexOutOfBoundsException(
                    "invalid range [" + left + ", " + right + "]"
            );
        }

        return prefix[right + 1] - prefix[left];
    }
}
