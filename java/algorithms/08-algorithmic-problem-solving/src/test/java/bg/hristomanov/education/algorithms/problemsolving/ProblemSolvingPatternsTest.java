package bg.hristomanov.education.algorithms.problemsolving;

import bg.hristomanov.education.algorithms.problemsolving.patterns.BacktrackingCombinations;
import bg.hristomanov.education.algorithms.problemsolving.patterns.PrefixSumRangeQuery;
import bg.hristomanov.education.algorithms.problemsolving.patterns.SlidingWindowLongestDistinct;
import bg.hristomanov.education.algorithms.problemsolving.patterns.TwoPointersPairSum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemSolvingPatternsTest {

    @Test
    void twoPointersEliminateImpossiblePairsFromBothEnds() {
        int[] values = {-3, 1, 4, 7, 12};

        Optional<TwoPointersPairSum.Pair> result =
                new TwoPointersPairSum().find(values, 8);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().leftValue()).isEqualTo(1);
        assertThat(result.orElseThrow().rightValue()).isEqualTo(7);
    }

    @Test
    void slidingWindowMaintainsAtMostKDistinctInvariant() {
        String text = "eceba";

        SlidingWindowLongestDistinct.Window window =
                new SlidingWindowLongestDistinct()
                        .longestSubstringWithAtMostKDistinct(text, 2);

        assertThat(window.length()).isEqualTo(3);
        assertThat(window.extractFrom(text)).isEqualTo("ece");
    }

    @Test
    void prefixSumTurnsRepeatedRangeAggregationIntoConstantTimeQuery() {
        PrefixSumRangeQuery query = new PrefixSumRangeQuery(new int[]{2, 5, -1, 4});

        assertThat(query.sumInclusive(1, 3)).isEqualTo(8);
        assertThat(query.sumInclusive(0, 0)).isEqualTo(2);
        assertThat(query.sumInclusive(0, 3)).isEqualTo(10);
    }

    @Test
    void backtrackingChooseExploreUndoEnumeratesAllCombinations() {
        List<List<String>> combinations =
                new BacktrackingCombinations().choose(List.of("A", "B", "C"), 2);

        assertThat(combinations)
                .containsExactly(
                        List.of("A", "B"),
                        List.of("A", "C"),
                        List.of("B", "C")
                );
    }
}
