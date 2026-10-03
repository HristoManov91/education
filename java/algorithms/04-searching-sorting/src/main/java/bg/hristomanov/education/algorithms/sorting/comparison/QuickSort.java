package bg.hristomanov.education.algorithms.sorting.comparison;

import bg.hristomanov.education.algorithms.sorting.model.SortMetrics;
import bg.hristomanov.education.algorithms.sorting.model.SortResult;

/**
 * In-place comparison sort.
 *
 * <p>Average time е O(n log n), но poor pivot behavior може да доведе до O(n²).
 * Използваме middle element като pivot за по-разбираема учебна реализация.</p>
 */
public final class QuickSort {

    public SortResult sort(int[] input) {
        int[] values = input.clone();
        Counter counter = new Counter();

        quickSort(values, 0, values.length - 1, counter);

        return new SortResult(values, new SortMetrics(counter.comparisons, counter.writes, 0));
    }

    private void quickSort(int[] values, int low, int high, Counter counter) {
        if (low >= high) {
            return;
        }

        int pivot = values[low + (high - low) / 2];
        int left = low;
        int right = high;

        while (left <= right) {
            while (true) {
                counter.comparisons++;
                if (values[left] >= pivot) {
                    break;
                }
                left++;
            }

            while (true) {
                counter.comparisons++;
                if (values[right] <= pivot) {
                    break;
                }
                right--;
            }

            if (left <= right) {
                swap(values, left, right, counter);
                left++;
                right--;
            }
        }

        if (low < right) {
            quickSort(values, low, right, counter);
        }
        if (left < high) {
            quickSort(values, left, high, counter);
        }
    }

    private void swap(int[] values, int left, int right, Counter counter) {
        if (left == right) {
            return;
        }

        int temporary = values[left];
        values[left] = values[right];
        values[right] = temporary;
        counter.writes += 2;
    }

    private static final class Counter {
        private long comparisons;
        private long writes;
    }
}
