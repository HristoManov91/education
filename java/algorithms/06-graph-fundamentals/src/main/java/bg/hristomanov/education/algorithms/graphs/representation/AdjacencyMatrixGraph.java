package bg.hristomanov.education.algorithms.graphs.representation;

import bg.hristomanov.education.algorithms.graphs.Graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph representation чрез adjacency matrix.
 *
 * <p>Lookup дали конкретна edge съществува е директен matrix access, но плащаме
 * V² cells независимо колко edges реално има graph-ът.</p>
 */
public final class AdjacencyMatrixGraph<T> implements Graph<T> {

    private final boolean directed;
    private final List<T> vertices;
    private final Map<T, Integer> indexByVertex;
    private final boolean[][] matrix;

    public AdjacencyMatrixGraph(Collection<T> vertices, boolean directed) {
        this.directed = directed;
        this.vertices = List.copyOf(new LinkedHashSet<>(vertices));
        this.indexByVertex = buildIndex(this.vertices);
        this.matrix = new boolean[this.vertices.size()][this.vertices.size()];
    }

    public void addEdge(T from, T to) {
        int fromIndex = requireIndex(from);
        int toIndex = requireIndex(to);

        matrix[fromIndex][toIndex] = true;
        if (!directed) {
            matrix[toIndex][fromIndex] = true;
        }
    }

    @Override
    public Set<T> vertices() {
        return Set.copyOf(vertices);
    }

    @Override
    public List<T> neighborsOf(T vertex) {
        int fromIndex = requireIndex(vertex);
        List<T> neighbors = new ArrayList<>();

        for (int toIndex = 0; toIndex < vertices.size(); toIndex++) {
            if (matrix[fromIndex][toIndex]) {
                neighbors.add(vertices.get(toIndex));
            }
        }

        return List.copyOf(neighbors);
    }

    @Override
    public boolean containsEdge(T from, T to) {
        return matrix[requireIndex(from)][requireIndex(to)];
    }

    @Override
    public boolean directed() {
        return directed;
    }

    public long storageCellCount() {
        return (long) vertices.size() * vertices.size();
    }

    public long storedTrueCells() {
        long count = 0;

        for (boolean[] row : matrix) {
            for (boolean cell : row) {
                if (cell) {
                    count++;
                }
            }
        }

        return count;
    }

    private Map<T, Integer> buildIndex(List<T> orderedVertices) {
        Map<T, Integer> result = new LinkedHashMap<>();

        for (int i = 0; i < orderedVertices.size(); i++) {
            T vertex = orderedVertices.get(i);
            if (result.put(vertex, i) != null) {
                throw new IllegalArgumentException("duplicate vertex: " + vertex);
            }
        }

        return Map.copyOf(result);
    }

    private int requireIndex(T vertex) {
        Integer index = indexByVertex.get(vertex);
        if (index == null) {
            throw new IllegalArgumentException("unknown vertex: " + vertex);
        }

        return index;
    }
}
