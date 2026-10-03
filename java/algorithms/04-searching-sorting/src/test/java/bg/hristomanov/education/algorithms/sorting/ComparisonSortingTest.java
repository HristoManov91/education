package bg.hristomanov.education.algorithms.sorting;

import bg.hristomanov.education.algorithms.sorting.comparison.InsertionSort;
import bg.hristomanov.education.algorithms.sorting.comparison.MergeSort;
import bg.hristomanov.education.algorithms.sorting.comparison.QuickSort;
import bg.hristomanov.education.algorithms.sorting.model.SortResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonSortingTest {

    @Test
    void allComparisonSortsProduceTheSameOrderedResult() {
        int[] input = {7, 1, 9, 4, 4, 2, 8, 0, 5, 3, 6};
        int[] expected = input.clone();
        Arrays.sort(expected);

        assertThat(new InsertionSort().sort(input).values()).containsExactly(expected);
        assertThat(new MergeSort().sort(input).values()).containsExactly(expected);
        assertThat(new QuickSort().sort(input).values()).containsExactly(expected);
    }

    @Test
    void insertionSortShowsQuadraticComparisonGrowthOnReverseInput() {
        int[] input100 = IntStream.iterate(100, value -> value - 1).limit(100).toArray();
        int[] input1000 = IntStream.iterate(1_000, value -> value - 1).limit(1_000).toArray();

        SortResult small = new InsertionSort().sort(input100);
        SortResult large = new InsertionSort().sort(input1000);

        assertThat(small.metrics().comparisons()).isEqualTo(4_950);
        assertThat(large.metrics().comparisons()).isEqualTo(499_500);
        assertThat(large.metrics().comparisons()).isGreaterThan(small.metrics().comparisons() * 100);
    }

    @Test
    void mergeSortMakesTheExtraMemoryTradeOffExplicit() {
        int[] input = {5, 4, 3, 2, 1};

        SortResult result = new MergeSort().sort(input);

        assertThat(result.values()).containsExactly(1, 2, 3, 4, 5);
        assertThat(result.metrics().auxiliarySlots()).isEqualTo(input.length);
    }
}
