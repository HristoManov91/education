package bg.hristomanov.education.algorithms.problemsolving;

import bg.hristomanov.education.algorithms.problemsolving.dp.advanced.MatrixChainMultiplication;
import bg.hristomanov.education.algorithms.problemsolving.dp.advanced.SubsetSumDynamicProgramming;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedDynamicProgrammingTest {

    @Test
    void subsetSumMakesTheNumericTargetPartOfTheStateSpace() {
        int[] values = {3, 34, 4, 12, 5, 2};
        SubsetSumDynamicProgramming subsetSum = new SubsetSumDynamicProgramming();

        SubsetSumDynamicProgramming.Result reachable = subsetSum.canReach(values, 9);
        SubsetSumDynamicProgramming.Result unreachable = subsetSum.canReach(values, 30);

        assertThat(reachable.reachable()).isTrue();
        assertThat(reachable.stateSlots()).isEqualTo(10);

        assertThat(unreachable.reachable()).isFalse();
        assertThat(unreachable.stateSlots()).isEqualTo(31);
    }

    @Test
    void intervalDpChoosesTheBestSplitForMatrixChain() {
        MatrixChainMultiplication.Result result =
                new MatrixChainMultiplication()
                        .minimumScalarMultiplications(new int[]{40, 20, 30, 10, 30});

        assertThat(result.minimumScalarMultiplications()).isEqualTo(26_000);
        assertThat(result.transitionChecks()).isGreaterThan(0);
    }
}
