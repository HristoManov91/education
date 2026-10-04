package bg.hristomanov.education.algorithms.graphalgorithms.shortestpath;

public record BellmanFordResult<T>(
        ShortestPathResult<T> paths,
        boolean reachableNegativeCycle,
        int passes,
        long relaxAttempts,
        long successfulRelaxations
) {
}
