package bg.hristomanov.education.algorithms.graphs.representation;

import bg.hristomanov.education.algorithms.graphs.Graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph representation чрез adjacency list.
 *
 * <p>Пазим само реално съществуващите edges, затова този layout е естествен
 * за sparse graph (граф с малко edges спрямо всички възможни връзки).</p>
 */
public final class AdjacencyListGraph<T> implements Graph<T> {

    private final boolean directed;
    private final Map<T, LinkedHashSet<T>> adjacency = new LinkedHashMap<>();

    public AdjacencyListGraph(boolean directed) {
        this.directed = directed;
    }

    public void addVertex(T vertex) {
        adjacency.computeIfAbsent(vertex, ignored -> new LinkedHashSet<>());
    }

    public void addEdge(T from, T to) {
        addVertex(from);
        addVertex(to);

        adjacency.get(from).add(to);
        if (!directed) {
            adjacency.get(to).add(from);
        }
    }

    @Override
    public Set<T> vertices() {
        return Set.copyOf(adjacency.keySet());
    }

    @Override
    public List<T> neighborsOf(T vertex) {
        LinkedHashSet<T> neighbors = adjacency.get(vertex);
        if (neighbors == null) {
            throw new IllegalArgumentException("unknown vertex: " + vertex);
        }

        return List.copyOf(neighbors);
    }

    @Override
    public boolean containsEdge(T from, T to) {
        LinkedHashSet<T> neighbors = adjacency.get(from);
        return neighbors != null && neighbors.contains(to);
    }

    @Override
    public boolean directed() {
        return directed;
    }

    /**
     * Брой реално пазени neighbor references.
     * При undirected graph една logical edge се пази в двете посоки.
     */
    public long storedEdgeReferences() {
        long references = 0;

        for (LinkedHashSet<T> neighbors : adjacency.values()) {
            references += neighbors.size();
        }

        return references;
    }

    public List<T> insertionOrderedVertices() {
        return new ArrayList<>(adjacency.keySet());
    }
}
