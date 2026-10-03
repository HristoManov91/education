package bg.hristomanov.education.algorithms.complexity.model;

import java.util.List;

public record ValidationResult(
        List<Long> invalidProductIds,
        long comparisons,
        long indexBuildOperations,
        long membershipChecks
) {
    public long totalLogicalOperations() {
        return comparisons + indexBuildOperations + membershipChecks;
    }
}
