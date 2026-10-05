package bg.hristomanov.education.algorithms.graphalgorithms.shortestpath;

public record DijkstraMetrics(
        long relaxAttempts,
        long successfulRelaxations,
        long priorityQueuePolls,
        long staleEntriesSkipped
) {
}
