package bg.hristomanov.education.algorithms.searching;

import bg.hristomanov.education.algorithms.searching.model.SearchResult;

/**
 * Линейно търсене: проверява елементите един по един.
 */
public final class LinearSearch {

    public SearchResult search(int[] values, int target) {
        long comparisons = 0;

        for (int i = 0; i < values.length; i++) {
            comparisons++;
            if (values[i] == target) {
                return new SearchResult(i, comparisons);
            }
        }

        return new SearchResult(-1, comparisons);
    }
}
