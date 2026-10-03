package bg.hristomanov.education.algorithms.graphs.model;

import java.util.List;

public record PathResult<T>(
        List<T> path,
        int edgeCount,
        long edgeInspections
) {
    public boolean reachable() {
        return !path.isEmpty();
    }
}
