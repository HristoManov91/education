package bg.hristomanov.education.algorithms.searching;

import bg.hristomanov.education.algorithms.searching.model.SearchResult;

/**
 * Binary search върху ascending sorted array.
 *
 * <p>На всяка стъпка изхвърляме половината оставащ search space. Това е
 * причината growth-ът да е O(log n), а не самият синтаксис на while loop-а.</p>
 */
public final class BinarySearch {

    public SearchResult search(int[] sortedValues, int target) {
        int low = 0;
        int high = sortedValues.length - 1;
        long comparisons = 0;

        while (low <= high) {
            int middle = low + (high - low) / 2;
            comparisons++;

            if (sortedValues[middle] == target) {
                return new SearchResult(middle, comparisons);
            }

            if (sortedValues[middle] < target) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return new SearchResult(-1, comparisons);
    }

    public SearchResult firstOccurrence(int[] sortedValues, int target) {
        int low = 0;
        int high = sortedValues.length - 1;
        int foundIndex = -1;
        long comparisons = 0;

        while (low <= high) {
            int middle = low + (high - low) / 2;
            comparisons++;

            if (sortedValues[middle] >= target) {
                if (sortedValues[middle] == target) {
                    foundIndex = middle;
                }
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }

        return new SearchResult(foundIndex, comparisons);
    }
}
