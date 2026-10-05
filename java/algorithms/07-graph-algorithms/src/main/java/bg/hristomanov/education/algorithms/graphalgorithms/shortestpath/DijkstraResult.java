package bg.hristomanov.education.algorithms.graphalgorithms.shortestpath;

public record DijkstraResult<T>(
        ShortestPathResult<T> paths,
        DijkstraMetrics metrics
) {
}
