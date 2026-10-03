package bg.hristomanov.education.algorithms.searching;

import bg.hristomanov.education.algorithms.searching.bad.RepeatedLinearCatalogLookup;
import bg.hristomanov.education.algorithms.searching.good.RepeatedBinaryCatalogLookup;
import bg.hristomanov.education.algorithms.searching.model.BatchLookupResult;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatedCatalogLookupTest {

    @Test
    void repeatedQueriesBenefitFromAnAlreadySortedSearchSpace() {
        int[] catalog = IntStream.range(0, 4_096).toArray();
        int[] requested = {0, 17, 511, 1_024, 2_048, 4_095, 5_000, -1};

        RepeatedLinearCatalogLookup badLookup = new RepeatedLinearCatalogLookup();
        RepeatedBinaryCatalogLookup goodLookup = new RepeatedBinaryCatalogLookup();

        BatchLookupResult bad = badLookup.findAll(catalog, requested);
        BatchLookupResult good = goodLookup.findAll(catalog, requested);

        assertThat(good.indexes()).isEqualTo(bad.indexes());
        assertThat(good.comparisons()).isLessThan(100);
        assertThat(bad.comparisons()).isGreaterThan(10_000);
    }
}
