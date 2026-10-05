package bg.hristomanov.education.algorithms.graphalgorithms.shortestpath;

import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedDirectedGraph;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedEdge;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Dijkstra за directed graph с non-negative weights.
 *
 * <p>Използваме practical stale-entry strategy: при подобрено distance добавяме
 * нов PriorityQueue entry. Ако по-късно poll-нем стар snapshot, го skip-ваме.
 * Така не ни трябва decrease-key API.</p>
 */
public final class DijkstraShortestPaths {

    public <T> DijkstraResult<T> compute(WeightedDirectedGraph<T> graph, T source) {
        requireKnownSource(graph, source);
        requireNonNegativeEdges(graph);

        Map<T, Long> distances = initializeDistances(graph, source);
        Map<T, T> predecessors = new LinkedHashMap<>();
        PriorityQueue<NodeDistance<T>> frontier =
                new PriorityQueue<>(Comparator.comparingLong(NodeDistance::distance));

        frontier.add(new NodeDistance<>(source, 0));

        long relaxAttempts = 0;
        long successfulRelaxations = 0;
        long priorityQueuePolls = 0;
        long staleEntriesSkipped = 0;

        while (!frontier.isEmpty()) {
            NodeDistance<T> current = frontier.poll();
            priorityQueuePolls++;

            long currentBest = distances.get(current.vertex());
            if (current.distance() != currentBest) {
                staleEntriesSkipped++;
                continue;
            }

            for (WeightedEdge<T> edge : graph.outgoingEdgesOf(current.vertex())) {
                relaxAttempts++;

                long candidate = ShortestPathMath.add(currentBest, edge.weight());
                long known = distances.get(edge.to());

                if (candidate < known) {
                    distances.put(edge.to(), candidate);
                    predecessors.put(edge.to(), current.vertex());
                    frontier.add(new NodeDistance<>(edge.to(), candidate));
                    successfulRelaxations++;
                }
            }
        }

        return new DijkstraResult<>(
                new ShortestPathResult<>(source, distances, predecessors),
                new DijkstraMetrics(
                        relaxAttempts,
                        successfulRelaxations,
                        priorityQueuePolls,
                        staleEntriesSkipped
                )
        );
    }

    private <T> Map<T, Long> initializeDistances(
            WeightedDirectedGraph<T> graph,
            T source
    ) {
        Map<T, Long> distances = new LinkedHashMap<>();

        for (T vertex : graph.orderedVertices()) {
            distances.put(vertex, ShortestPathMath.INFINITY);
        }

        distances.put(source, 0L);
        return distances;
    }

    private <T> void requireKnownSource(WeightedDirectedGraph<T> graph, T source) {
        if (!graph.vertices().contains(source)) {
            throw new IllegalArgumentException("unknown source vertex: " + source);
        }
    }

    private <T> void requireNonNegativeEdges(WeightedDirectedGraph<T> graph) {
        for (WeightedEdge<T> edge : graph.edges()) {
            if (edge.weight() < 0) {
                throw new IllegalArgumentException(
                        "Dijkstra requires non-negative weights, found: " + edge
                );
            }
        }
    }

    private record NodeDistance<T>(T vertex, long distance) {
    }
}
