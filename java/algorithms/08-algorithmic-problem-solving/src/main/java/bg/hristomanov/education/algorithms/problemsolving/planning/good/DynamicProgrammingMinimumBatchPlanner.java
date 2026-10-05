package bg.hristomanov.education.algorithms.problemsolving.planning.good;

import bg.hristomanov.education.algorithms.problemsolving.planning.BatchPlanResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Bottom-up DP за същия minimum-batch problem.
 *
 * <p>State = processed targetUnits amount.
 * dp[x] = minimum batches required to build exactly x units.</p>
 */
public final class DynamicProgrammingMinimumBatchPlanner {

    public BatchPlanResult plan(int targetUnits, int[] allowedBatchSizes) {
        validate(targetUnits, allowedBatchSizes);

        int unreachable = targetUnits + 1;
        int[] minimum = new int[targetUnits + 1];
        int[] chosenBatch = new int[targetUnits + 1];
        Arrays.fill(minimum, unreachable);
        Arrays.fill(chosenBatch, -1);
        minimum[0] = 0;

        long transitionChecks = 0;

        for (int current = 1; current <= targetUnits; current++) {
            for (int batchSize : allowedBatchSizes) {
                transitionChecks++;

                if (batchSize <= current
                        && minimum[current - batchSize] != unreachable
                        && minimum[current - batchSize] + 1 < minimum[current]) {
                    minimum[current] = minimum[current - batchSize] + 1;
                    chosenBatch[current] = batchSize;
                }
            }
        }

        if (minimum[targetUnits] == unreachable) {
            return new BatchPlanResult(-1, List.of(), transitionChecks);
        }

        List<Integer> selected = new ArrayList<>();
        int remaining = targetUnits;

        while (remaining > 0) {
            int batchSize = chosenBatch[remaining];
            selected.add(batchSize);
            remaining -= batchSize;
        }

        Collections.reverse(selected);
        return new BatchPlanResult(
                minimum[targetUnits],
                List.copyOf(selected),
                transitionChecks
        );
    }

    private void validate(int targetUnits, int[] allowedBatchSizes) {
        if (targetUnits < 0) {
            throw new IllegalArgumentException("targetUnits must be >= 0");
        }
        for (int batchSize : allowedBatchSizes) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batch sizes must be > 0");
            }
        }
    }
}
