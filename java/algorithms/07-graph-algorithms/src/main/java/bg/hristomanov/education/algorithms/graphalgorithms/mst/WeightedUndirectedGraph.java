package bg.hristomanov.education.algorithms.graphalgorithms.mst;

import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal weighted undirected graph за Kruskal lab.
 *
 * <p>Всяка logical undirected edge се пази точно веднъж в edges list-а.</p>
 */
public final class WeightedUndirectedGraph<T> {

    private final Set<T> vertices = new LinkedHashSet<>();
    private final List<WeightedEdge<T>> edges = new ArrayList<>();

    public void addVertex(T vertex) {
        vertices.add(vertex);
    }

    public void addEdge(T first, T second, long weight) {
        addVertex(first);
        addVertex(second);
        edges.add(new WeightedEdge<>(first, second, weight));
    }

    public Set<T> vertices() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(vertices));
    }

    public List<WeightedEdge<T>> edges() {
        return List.copyOf(edges);
    }

    public int vertexCount() {
        return vertices.size();
    }
}
