package bg.hristomanov.education.algorithms.graphs.traversal;

import bg.hristomanov.education.algorithms.graphs.Graph;
import bg.hristomanov.education.algorithms.graphs.model.PathResult;
import bg.hristomanov.education.algorithms.graphs.model.TraversalResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Breadth-First Search (BFS) използва Queue и обхожда graph-а layer by layer.
 */
public final class BreadthFirstSearch {

    public <T> TraversalResult<T> traverse(Graph<T> graph, T start) {
        requireVertex(graph, start);

        List<T> order = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        Deque<T> queue = new ArrayDeque<>();

        visited.add(start);
        queue.addLast(start);

        long edgeInspections = 0;
        int maxQueueSize = 1;

        while (!queue.isEmpty()) {
            T current = queue.removeFirst();
            order.add(current);

            for (T neighbor : graph.neighborsOf(current)) {
                edgeInspections++;

                // Mark при discovery, не при dequeue. Иначе един node може да
                // попадне в queue многократно през различни incoming edges.
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                    maxQueueSize = Math.max(maxQueueSize, queue.size());
                }
            }
        }

        return new TraversalResult<>(List.copyOf(order), edgeInspections, maxQueueSize);
    }

    /**
     * В unweighted graph първото discovery на target е по path с минимален брой edges.
     */
    public <T> PathResult<T> shortestPath(Graph<T> graph, T start, T target) {
        requireVertex(graph, start);
        requireVertex(graph, target);

        if (start.equals(target)) {
            return new PathResult<>(List.of(start), 0, 0);
        }

        Set<T> visited = new HashSet<>();
        Map<T, T> parent = new HashMap<>();
        Deque<T> queue = new ArrayDeque<>();

        visited.add(start);
        queue.addLast(start);
        long edgeInspections = 0;

        while (!queue.isEmpty()) {
            T current = queue.removeFirst();

            for (T neighbor : graph.neighborsOf(current)) {
                edgeInspections++;

                if (!visited.add(neighbor)) {
                    continue;
                }

                parent.put(neighbor, current);

                if (neighbor.equals(target)) {
                    List<T> path = reconstructPath(parent, start, target);
                    return new PathResult<>(path, path.size() - 1, edgeInspections);
                }

                queue.addLast(neighbor);
            }
        }

        return new PathResult<>(List.of(), -1, edgeInspections);
    }

    private <T> List<T> reconstructPath(Map<T, T> parent, T start, T target) {
        LinkedList<T> path = new LinkedList<>();
        T current = target;

        while (!current.equals(start)) {
            path.addFirst(current);
            current = parent.get(current);
        }

        path.addFirst(start);
        return List.copyOf(path);
    }

    private <T> void requireVertex(Graph<T> graph, T vertex) {
        if (!graph.vertices().contains(vertex)) {
            throw new IllegalArgumentException("unknown vertex: " + vertex);
        }
    }
}
