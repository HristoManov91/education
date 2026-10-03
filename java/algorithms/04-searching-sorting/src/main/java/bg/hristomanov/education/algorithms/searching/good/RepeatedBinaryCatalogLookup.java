package bg.hristomanov.education.algorithms.searching.good;

import bg.hristomanov.education.algorithms.searching.BinarySearch;
import bg.hristomanov.education.algorithms.searching.model.BatchLookupResult;
import bg.hristomanov.education.algorithms.searching.model.SearchResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Използва факта, че каталогът е sorted, и намалява search space-а наполовина
 * при всяка comparison.
 */
public final class RepeatedBinaryCatalogLookup {

    private final BinarySearch binarySearch = new BinarySearch();

    public BatchLookupResult findAll(int[] sortedCatalogIds, int[] requestedIds) {
        List<Integer> indexes = new ArrayList<>(requestedIds.length);
        long comparisons = 0;

        for (int requestedId : requestedIds) {
            SearchResult result = binarySearch.search(sortedCatalogIds, requestedId);
            indexes.add(result.index());
            comparisons += result.comparisons();
        }

        return new BatchLookupResult(List.copyOf(indexes), comparisons);
    }
}
