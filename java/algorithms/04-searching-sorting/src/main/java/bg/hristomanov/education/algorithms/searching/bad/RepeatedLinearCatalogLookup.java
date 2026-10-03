package bg.hristomanov.education.algorithms.searching.bad;

import bg.hristomanov.education.algorithms.searching.LinearSearch;
import bg.hristomanov.education.algorithms.searching.model.BatchLookupResult;
import bg.hristomanov.education.algorithms.searching.model.SearchResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Реалистичен bad вариант: имаме вече sorted/read-mostly каталог, но за всяка
 * заявка продължаваме да го сканираме линейно.
 */
public final class RepeatedLinearCatalogLookup {

    private final LinearSearch linearSearch = new LinearSearch();

    public BatchLookupResult findAll(int[] sortedCatalogIds, int[] requestedIds) {
        List<Integer> indexes = new ArrayList<>(requestedIds.length);
        long comparisons = 0;

        for (int requestedId : requestedIds) {
            SearchResult result = linearSearch.search(sortedCatalogIds, requestedId);
            indexes.add(result.index());
            comparisons += result.comparisons();
        }

        return new BatchLookupResult(List.copyOf(indexes), comparisons);
    }
}
