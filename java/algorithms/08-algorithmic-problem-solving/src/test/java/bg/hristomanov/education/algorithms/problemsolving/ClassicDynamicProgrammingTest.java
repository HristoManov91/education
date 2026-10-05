package bg.hristomanov.education.algorithms.problemsolving;

import bg.hristomanov.education.algorithms.problemsolving.dp.classic.CoinChangeDynamicProgramming;
import bg.hristomanov.education.algorithms.problemsolving.dp.classic.LongestCommonSubsequence;
import bg.hristomanov.education.algorithms.problemsolving.dp.classic.LongestIncreasingSubsequence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassicDynamicProgrammingTest {

    @Test
    void lcsUsesTwoDimensionalStateToCoordinateTwoSequences() {
        LongestCommonSubsequence.Result result =
                new LongestCommonSubsequence().solve("ABCBDAB", "BDCABA");

        assertThat(result.length()).isEqualTo(4);
        assertThat(result.sequence()).hasSize(4);
        assertThat(isSubsequence(result.sequence(), "ABCBDAB")).isTrue();
        assertThat(isSubsequence(result.sequence(), "BDCABA")).isTrue();
    }

    @Test
    void lisStateMeansBestIncreasingSequenceEndingAtThisPosition() {
        int[] values = {10, 9, 2, 5, 3, 7, 101, 18};

        LongestIncreasingSubsequence.Result result =
                new LongestIncreasingSubsequence().solve(values);

        assertThat(result.length()).isEqualTo(4);
        assertThat(result.sequence()).hasSize(4);
        assertThat(isStrictlyIncreasing(result.sequence())).isTrue();
    }

    @Test
    void sameCoinInputSupportsDifferentObjectivesAndDifferentDpSemantics() {
        CoinChangeDynamicProgramming coinChange = new CoinChangeDynamicProgramming();

        CoinChangeDynamicProgramming.MinimumCoinsResult minimum =
                coinChange.minimumCoins(new int[]{1, 3, 4}, 6);

        CoinChangeDynamicProgramming.CountWaysResult ways =
                coinChange.countCombinations(new int[]{1, 2, 5}, 5);

        assertThat(minimum.minimumCoinCount()).isEqualTo(2);
        assertThat(minimum.coins()).hasSize(2);
        assertThat(minimum.coins()).allMatch(coin -> coin == 3);

        assertThat(ways.ways()).isEqualTo(4);
    }

    private boolean isSubsequence(String candidate, String source) {
        int candidateIndex = 0;

        for (int sourceIndex = 0;
             sourceIndex < source.length() && candidateIndex < candidate.length();
             sourceIndex++) {
            if (source.charAt(sourceIndex) == candidate.charAt(candidateIndex)) {
                candidateIndex++;
            }
        }

        return candidateIndex == candidate.length();
    }

    private boolean isStrictlyIncreasing(java.util.List<Integer> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i - 1) >= values.get(i)) {
                return false;
            }
        }

        return true;
    }
}
