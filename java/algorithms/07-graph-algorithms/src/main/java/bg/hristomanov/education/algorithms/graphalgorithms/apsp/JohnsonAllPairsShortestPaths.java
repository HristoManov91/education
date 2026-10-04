package bg.hristomanov.education.algorithms.graphalgorithms.apsp;

import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.ShortestPathResult;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedDirectedGraph;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedEdge;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Johnson APSP: Bellman-Ford-style potentials + non-negative reweighting
 * + Dijkstra from every source.
 *
 * <p>За potentials използваме еквивалентната super-source идея: initial h(v)=0
 * за всички vertices, все едно synthetic source има zero edge към всеки node.</p>
 */
public final class JohnsonAllPairsShortestPaths {

    private static final long INFINITY = ShortestPathResult.infinity();

    public <T> JohnsonResult<T> compute(WeightedDirectedGraph<T> graph) {
        Map<T, Long> potentials = computePotentials(graph);
        Map<T, Map<T, Long>> allPairs = new LinkedHashMap<>();

        for (T source : graph.orderedVertices()) {
            Map<T, Long> reweightedDistances = dijkstraReweighted(graph, source, potentials);
            Map<T, Long> originalDistances = new LinkedHashMap<>();

            for (T target : graph.orderedVertices()) {
                long reweighted = reweightedDistances.get(target);

                if (reweighted == INFINITY) {
                    originalDistances.put(target, INFINITY);
                    continue;
                }

                long original = Math.addExact(
                        Math.subtractExact(reweighted, potentials.get(source)),
                        potentials.get(target)
                );
                originalDistances.put(target, original);
            }

            allPairs.put(source, Map.copyOf(originalDistances));
        }

        return new JohnsonResult<>(
                new AllPairsShortestPathResult<>(allPairs),
                Map.copyOf(potentials)
        );
    }

    private <T> Map<T, Long> computePotentials(WeightedDirectedGraph<T> graph) {
        Map<T, Long> potentials = new LinkedHashMap<>();

        // Equivalent to adding a super-source with 0-weight edge to every vertex.
        for (T vertex : graph.orderedVertices()) {
            potentials.put(vertex, 0L);
        }

        for (int pass = 1; pass < graph.vertexCount(); pass++) {
            boolean changed = false;

            for (WeightedEdge<T> edge : graph.edges()) {
                long candidate = Math.addExact(potentials.get(edge.from()), edge.weight());

                if (candidate < potentials.get(edge.to())) {
                    potentials.put(edge.to(), candidate);
                    changed = true;
                }
            }

            if (!changed) {
                break;
            }
        }

        for (WeightedEdge<T> edge : graph.edges()) {
            long candidate = Math.addExact(potentials.get(edge.from()), edge.weight());

            if (candidate < potentials.get(edge.to())) {
                throw new IllegalArgumentException(
                        "Johnson cannot run on a graph with a negative cycle"
                );
            }
        }

        return potentials;
    }

    private <T> Map<T, Long> dijkstraReweighted(
            WeightedDirectedGraph<T> graph,
            T source,
            Map<T, Long> potentials
    ) {
        Map<T, Long> distances = new LinkedHashMap<>();

        for (T vertex : graph.orderedVertices()) {
            distances.put(vertex, INFINITY);
        }

        distances.put(source, 0L);

        PriorityQueue<NodeDistance<T>> queue =
                new PriorityQueue<>(Comparator.comparingLong(NodeDistance::distance));
        queue.add(new NodeDistance<>(source, 0));

        while (!queue.isEmpty()) {
            NodeDistance<T> current = queue.poll();

            if (current.distance() != distances.get(current.vertex())) {
                continue;
            }

            for (WeightedEdge<T> edge : graph.outgoingEdgesOf(current.vertex())) {
                long reweighted = reweightedWeight(edge, potentials);

                if (reweighted < 0) {
                    throw new IllegalStateException(
                            "Johnson reweighting produced a negative edge: " + edge
                    );
                }

                long candidate = Math.addExact(current.distance(), reweighted);

                if (candidate < distances.get(edge.to())) {
                    distances.put(edge.to(), candidate);
                    queue.add(new NodeDistance<>(edge.to(), candidate));
                }
            }
        }

        return distances;
    }

    private <T> long reweightedWeight(
            WeightedEdge<T> edge,
            Map<T, Long> potentials
    ) {
        return Math.addExact(
                Math.addExact(edge.weight(), potentials.get(edge.from())),
                -potentials.get(edge.to())
        );
    }

    private record NodeDistance<T>(T vertex, long distance) {
    }
}
