package bg.hristomanov.education.algorithms.graphalgorithms.unionfind;

public record UnionFindMetrics(
        long parentTraversals,
        long pathCompressions,
        long successfulUnions
) {
}
