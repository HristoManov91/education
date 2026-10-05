package bg.hristomanov.education.algorithms.problemsolving;

import bg.hristomanov.education.algorithms.problemsolving.planning.BatchPlanResult;
import bg.hristomanov.education.algorithms.problemsolving.planning.bad.RecursiveMinimumBatchPlanner;
import bg.hristomanov.education.algorithms.problemsolving.planning.good.DynamicProgrammingMinimumBatchPlanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinimumBatchPlannerTradeOffTest {

    @Test
    void dpPreservesTheBusinessAnswerWhileEliminatingRepeatedSubproblems() {
        int targetUnits = 20;
        int[] allowedBatchSizes = {1, 4, 6};

        BatchPlanResult naive =
                new RecursiveMinimumBatchPlanner().plan(targetUnits, allowedBatchSizes);

        BatchPlanResult dp =
                new DynamicProgrammingMinimumBatchPlanner().plan(
                        targetUnits,
                        allowedBatchSizes
                );

        assertThat(naive.minimumBatchCount()).isEqualTo(4);
        assertThat(dp.minimumBatchCount()).isEqualTo(naive.minimumBatchCount());

        assertThat(dp.batchSizes())
                .hasSize(4)
                .allMatch(batch -> batch == 4 || batch == 6);

        assertThat(naive.statesOrCallsEvaluated())
                .isGreaterThan(dp.statesOrCallsEvaluated() * 10);
    }

    @Test
    void bothVariantsReportImpossibleTargetConsistently() {
        int[] allowedBatchSizes = {4, 6};

        BatchPlanResult naive =
                new RecursiveMinimumBatchPlanner().plan(7, allowedBatchSizes);

        BatchPlanResult dp =
                new DynamicProgrammingMinimumBatchPlanner().plan(7, allowedBatchSizes);

        assertThat(naive.possible()).isFalse();
        assertThat(dp.possible()).isFalse();
    }
}
