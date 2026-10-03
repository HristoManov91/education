package bg.hristomanov.education.algorithms.graphs.model;

import java.util.List;

public record TraversalResult<T>(
        List<T> order,
        long edgeInspections,
        int maxWorkingSetSize
) {
}
