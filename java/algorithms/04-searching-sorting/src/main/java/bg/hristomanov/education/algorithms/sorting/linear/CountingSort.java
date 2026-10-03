package bg.hristomanov.education.algorithms.sorting.linear;

import bg.hristomanov.education.algorithms.sorting.model.SortMetrics;
import bg.hristomanov.education.algorithms.sorting.model.SortResult;

/**
 * Counting sort за non-negative integer keys в известен bounded range.
 *
 * <p>Time complexity е O(n + k), където k е key range size. Това не нарушава
 * comparison-sorting lower bound, защото използваме допълнителна информация за keys.</p>
 */
public final class CountingSort {

    public SortResult sort(int[] input, int maxKey) {
        if (maxKey < 0) {
            throw new IllegalArgumentException("maxKey must be >= 0");
        }

        int[] counts = new int[maxKey + 1];
        long writes = 0;

        for (int value : input) {
            if (value < 0 || value > maxKey) {
                throw new IllegalArgumentException(
                        "value " + value + " is outside [0, " + maxKey + "]"
                );
            }
            counts[value]++;
            writes++;
        }

        int[] sorted = new int[input.length];
        int target = 0;

        for (int value = 0; value < counts.length; value++) {
            for (int count = 0; count < counts[value]; count++) {
                sorted[target++] = value;
                writes++;
            }
        }

        return new SortResult(sorted, new SortMetrics(0, writes, counts.length + sorted.length));
    }
}
