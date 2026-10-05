package bg.hristomanov.education.algorithms.graphalgorithms.shortestpath;

import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedDirectedGraph;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedEdge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bellman-Ford: repeated relaxation върху всички edges.
 *
 * <p>Поддържа negative weights и открива reachable negative cycle чрез
 * допълнителен relaxation pass след максимум V - 1 passes.</p>
 */
public final class BellmanFordShortestPaths {

    public <T> BellmanFordResult<T> compute(WeightedDirectedGraph<T> graph, T source) {
        requireKnownSource(graph, source);

        Map<T, Long> distances = initializeDistances(graph, source);
        Map<T, T> predecessors = new LinkedHashMap<>();

        int passes = 0;
        long relaxAttempts = 0;
        long successfulRelaxations = 0;

        for (int pass = 1; pass < graph.vertexCount(); pass++) {
            boolean changed = false;
            passes++;

            for (WeightedEdge<T> edge : graph.edges()) {
                relaxAttempts++;

                long fromDistance = distances.get(edge.from());
                if (fromDistance == ShortestPathMath.INFINITY) {
                    continue;
                }

                long candidate = ShortestPathMath.add(fromDistance, edge.weight());
                if (candidate < distances.get(edge.to())) {
                    distances.put(edge.to(), candidate);
                    predecessors.put(edge.to(), edge.from());
                    successfulRelaxations++;
                    changed = true;
                }
            }

            if (!changed) {
                break;
            }
        }

        boolean reachableNegativeCycle = hasReachableRelaxableEdge(graph, distances);

        return new BellmanFordResult<>(
                new ShortestPathResult<>(source, distances, predecessors),
                reachableNegativeCycle,
                passes,
                relaxAttempts,
                successfulRelaxations
        );
    }

    private <T> boolean hasReachableRelaxableEdge(
            WeightedDirectedGraph<T> graph,
            Map<T, Long> distances
    ) {
        for (WeightedEdge<T> edge : graph.edges()) {
            long fromDistance = distances.get(edge.from());

            if (fromDistance == ShortestPathMath.INFINITY) {
                continue;
            }

            long candidate = ShortestPathMath.add(fromDistance, edge.weight());
            if (candidate < distances.get(edge.to())) {
                return true;
            }
        }

        return false;
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
}
