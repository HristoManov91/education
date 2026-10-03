package bg.hristomanov.education.algorithms.sorting.comparison;

import bg.hristomanov.education.algorithms.sorting.model.SortMetrics;
import bg.hristomanov.education.algorithms.sorting.model.SortResult;

/**
 * Stable comparison sort с O(n log n) worst-case time и O(n) auxiliary memory.
 */
public final class MergeSort {

    public SortResult sort(int[] input) {
        int[] values = input.clone();
        int[] buffer = new int[values.length];
        Counter counter = new Counter();

        sort(values, buffer, 0, values.length, counter);

        return new SortResult(
                values,
                new SortMetrics(counter.comparisons, counter.writes, buffer.length)
        );
    }

    private void sort(int[] values, int[] buffer, int from, int to, Counter counter) {
        if (to - from <= 1) {
            return;
        }

        int middle = from + (to - from) / 2;
        sort(values, buffer, from, middle, counter);
        sort(values, buffer, middle, to, counter);
        merge(values, buffer, from, middle, to, counter);
    }

    private void merge(int[] values, int[] buffer, int from, int middle, int to, Counter counter) {
        int left = from;
        int right = middle;
        int target = from;

        while (left < middle && right < to) {
            counter.comparisons++;
            if (values[left] <= values[right]) {
                buffer[target++] = values[left++];
            } else {
                buffer[target++] = values[right++];
            }
            counter.writes++;
        }

        while (left < middle) {
            buffer[target++] = values[left++];
            counter.writes++;
        }

        while (right < to) {
            buffer[target++] = values[right++];
            counter.writes++;
        }

        for (int i = from; i < to; i++) {
            values[i] = buffer[i];
            counter.writes++;
        }
    }

    private static final class Counter {
        private long comparisons;
        private long writes;
    }
}
