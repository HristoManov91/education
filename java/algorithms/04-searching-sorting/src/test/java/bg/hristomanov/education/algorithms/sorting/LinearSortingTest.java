package bg.hristomanov.education.algorithms.sorting;

import bg.hristomanov.education.algorithms.sorting.linear.CountingSort;
import bg.hristomanov.education.algorithms.sorting.linear.RadixSort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinearSortingTest {

    @Test
    void countingSortUsesBoundedIntegerKeySpaceInsteadOfPairwiseComparisons() {
        int[] input = {4, 2, 2, 8, 3, 3, 1};

        int[] sorted = new CountingSort().sort(input, 8).values();

        assertThat(sorted).containsExactly(1, 2, 2, 3, 3, 4, 8);
    }

    @Test
    void countingSortRejectsValuesOutsideItsDeclaredKeyRange() {
        assertThatThrownBy(() -> new CountingSort().sort(new int[]{1, 5, 9}, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void radixSortMatchesGeneralPurposeSortingForNonNegativeIntegers() {
        int[] input = {170, 45, 75, 90, 802, 24, 2, 66, 5, 1000};
        int[] expected = input.clone();
        Arrays.sort(expected);

        int[] actual = new RadixSort().sort(input).values();

        assertThat(actual).containsExactly(expected);
    }

    @Test
    void radixSortRejectsNegativeIntegersBecauseTheImplementationAssumptionIsExplicit() {
        assertThatThrownBy(() -> new RadixSort().sort(new int[]{10, -2, 30}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }
}
