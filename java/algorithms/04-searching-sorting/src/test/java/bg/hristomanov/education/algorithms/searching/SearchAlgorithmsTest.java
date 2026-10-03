package bg.hristomanov.education.algorithms.searching;

import bg.hristomanov.education.algorithms.searching.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SearchAlgorithmsTest {

    private final LinearSearch linearSearch = new LinearSearch();
    private final BinarySearch binarySearch = new BinarySearch();

    @Test
    void binarySearchShrinksTheSearchSpaceLogarithmically() {
        int[] values = IntStream.range(0, 1_024).toArray();

        SearchResult linear = linearSearch.search(values, -1);
        SearchResult binary = binarySearch.search(values, -1);

        assertThat(linear.found()).isFalse();
        assertThat(binary.found()).isFalse();
        assertThat(linear.comparisons()).isEqualTo(1_024);
        assertThat(binary.comparisons()).isLessThanOrEqualTo(11);
    }

    @Test
    void firstOccurrenceHandlesDuplicatesAndBoundaries() {
        int[] values = {1, 2, 2, 2, 5, 9};

        assertThat(binarySearch.firstOccurrence(values, 2).index()).isEqualTo(1);
        assertThat(binarySearch.firstOccurrence(values, 1).index()).isEqualTo(0);
        assertThat(binarySearch.firstOccurrence(values, 9).index()).isEqualTo(5);
        assertThat(binarySearch.firstOccurrence(values, 7).index()).isEqualTo(-1);
        assertThat(binarySearch.firstOccurrence(new int[0], 7).index()).isEqualTo(-1);
    }
}
