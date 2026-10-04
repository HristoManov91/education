package bg.hristomanov.education.algorithms.graphalgorithms.weighted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Directed weighted graph чрез adjacency list.
 *
 * <p>Weighted shortest-path algorithms iterate-ват outgoing edges, затова
 * adjacency-list representation е естествено за sparse graphs.</p>
 */
public final class WeightedDirectedGraph<T> {

    private final Map<T, List<WeightedEdge<T>>> adjacency = new LinkedHashMap<>();
    private final List<WeightedEdge<T>> edges = new ArrayList<>();

    public void addVertex(T vertex) {
        adjacency.computeIfAbsent(vertex, ignored -> new ArrayList<>());
    }

    public void addEdge(T from, T to, long weight) {
        addVertex(from);
        addVertex(to);

        WeightedEdge<T> edge = new WeightedEdge<>(from, to, weight);
        adjacency.get(from).add(edge);
        edges.add(edge);
    }

    public Set<T> vertices() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(adjacency.keySet()));
    }

    public List<T> orderedVertices() {
        return List.copyOf(adjacency.keySet());
    }

    public List<WeightedEdge<T>> outgoingEdgesOf(T vertex) {
        List<WeightedEdge<T>> outgoing = adjacency.get(vertex);
        if (outgoing == null) {
            throw new IllegalArgumentException("unknown vertex: " + vertex);
        }

        return List.copyOf(outgoing);
    }

    public List<WeightedEdge<T>> edges() {
        return List.copyOf(edges);
    }

    public int vertexCount() {
        return adjacency.size();
    }

    public int edgeCount() {
        return edges.size();
    }
}
