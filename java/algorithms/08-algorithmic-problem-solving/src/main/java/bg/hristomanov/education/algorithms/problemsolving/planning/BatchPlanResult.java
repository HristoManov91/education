package bg.hristomanov.education.algorithms.problemsolving.planning;

import java.util.List;

public record BatchPlanResult(
        int minimumBatchCount,
        List<Integer> batchSizes,
        long statesOrCallsEvaluated
) {
    public BatchPlanResult {
        batchSizes = List.copyOf(batchSizes);
    }

    public boolean possible() {
        return minimumBatchCount >= 0;
    }
}
