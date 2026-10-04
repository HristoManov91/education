package bg.hristomanov.education.algorithms.graphalgorithms.mst;

import bg.hristomanov.education.algorithms.graphalgorithms.unionfind.UnionFindMetrics;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedEdge;

import java.util.List;

public record MstResult<T>(
        List<WeightedEdge<T>> edges,
        long totalWeight,
        boolean spanning,
        UnionFindMetrics unionFindMetrics
) {
    public MstResult {
        edges = List.copyOf(edges);
    }
}
