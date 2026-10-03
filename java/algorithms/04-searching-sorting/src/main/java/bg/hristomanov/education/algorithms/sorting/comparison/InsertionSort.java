package bg.hristomanov.education.algorithms.sorting.comparison;

import bg.hristomanov.education.algorithms.sorting.model.SortMetrics;
import bg.hristomanov.education.algorithms.sorting.model.SortResult;

/**
 * Stable, in-place comparison sort.
 *
 * <p>Силен при малки или nearly-sorted inputs, но worst-case work-ът е O(n²).</p>
 */
public final class InsertionSort {

    public SortResult sort(int[] input) {
        int[] values = input.clone();
        long comparisons = 0;
        long writes = 0;

        for (int i = 1; i < values.length; i++) {
            int current = values[i];
            int position = i - 1;

            while (position >= 0) {
                comparisons++;
                if (values[position] <= current) {
                    break;
                }

                values[position + 1] = values[position];
                writes++;
                position--;
            }

            values[position + 1] = current;
            writes++;
        }

        return new SortResult(values, new SortMetrics(comparisons, writes, 0));
    }
}
