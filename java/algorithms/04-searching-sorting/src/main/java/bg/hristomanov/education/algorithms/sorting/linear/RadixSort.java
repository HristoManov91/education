package bg.hristomanov.education.algorithms.sorting.linear;

import bg.hristomanov.education.algorithms.sorting.model.SortMetrics;
import bg.hristomanov.education.algorithms.sorting.model.SortResult;

/**
 * LSD radix sort за non-negative decimal integers.
 *
 * <p>Всеки digit pass използва stable counting distribution. Алгоритъмът не
 * сравнява произволни pairs; използва representation-а на integer keys.</p>
 */
public final class RadixSort {

    private static final int RADIX = 10;

    public SortResult sort(int[] input) {
        int[] values = input.clone();

        int max = 0;
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("radix sort supports non-negative integers only");
            }
            max = Math.max(max, value);
        }

        long writes = 0;
        int[] output = new int[values.length];

        for (long divisor = 1; max / divisor > 0; divisor *= RADIX) {
            int[] counts = new int[RADIX];

            for (int value : values) {
                int digit = (int) ((value / divisor) % RADIX);
                counts[digit]++;
                writes++;
            }

            for (int i = 1; i < counts.length; i++) {
                counts[i] += counts[i - 1];
                writes++;
            }

            for (int i = values.length - 1; i >= 0; i--) {
                int digit = (int) ((values[i] / divisor) % RADIX);
                output[--counts[digit]] = values[i];
                writes++;
            }

            System.arraycopy(output, 0, values, 0, values.length);
            writes += values.length;

            if (divisor > Integer.MAX_VALUE / RADIX) {
                break;
            }
        }

        return new SortResult(values, new SortMetrics(0, writes, values.length + RADIX));
    }
}
