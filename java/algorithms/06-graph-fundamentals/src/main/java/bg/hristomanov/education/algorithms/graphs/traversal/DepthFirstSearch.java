package bg.hristomanov.education.algorithms.graphs.traversal;

import bg.hristomanov.education.algorithms.graphs.Graph;
import bg.hristomanov.education.algorithms.graphs.model.TraversalResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Depth-First Search (DFS) следва един branch в дълбочина, преди да backtrack-не.
 */
public final class DepthFirstSearch {

    public <T> TraversalResult<T> traverseRecursive(Graph<T> graph, T start) {
        requireVertex(graph, start);

        List<T> order = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        MutableCounter edgeInspections = new MutableCounter();
        MutableMaximum maxDepth = new MutableMaximum();

        dfsRecursive(graph, start, 1, visited, order, edgeInspections, maxDepth);

        return new TraversalResult<>(
                List.copyOf(order),
                edgeInspections.value,
                maxDepth.value
        );
    }

    private <T> void dfsRecursive(
            Graph<T> graph,
            T current,
            int depth,
            Set<T> visited,
            List<T> order,
            MutableCounter edgeInspections,
            MutableMaximum maxDepth
    ) {
        visited.add(current);
        order.add(current);
        maxDepth.value = Math.max(maxDepth.value, depth);

        for (T neighbor : graph.neighborsOf(current)) {
            edgeInspections.value++;

            if (!visited.contains(neighbor)) {
                dfsRecursive(
                        graph,
                        neighbor,
                        depth + 1,
                        visited,
                        order,
                        edgeInspections,
                        maxDepth
                );
            }
        }
    }

    public <T> TraversalResult<T> traverseIterative(Graph<T> graph, T start) {
        requireVertex(graph, start);

        List<T> order = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        Deque<T> stack = new ArrayDeque<>();

        stack.push(start);
        long edgeInspections = 0;
        int maxStackSize = 1;

        while (!stack.isEmpty()) {
            T current = stack.pop();

            if (!visited.add(current)) {
                continue;
            }

            order.add(current);

            List<T> neighbors = new ArrayList<>(graph.neighborsOf(current));
            Collections.reverse(neighbors);

            for (T neighbor : neighbors) {
                edgeInspections++;
                if (!visited.contains(neighbor)) {
                    stack.push(neighbor);
                    maxStackSize = Math.max(maxStackSize, stack.size());
                }
            }
        }

        return new TraversalResult<>(List.copyOf(order), edgeInspections, maxStackSize);
    }

    public <T> List<List<T>> connectedComponents(Graph<T> graph) {
        if (graph.directed()) {
            throw new IllegalArgumentException(
                    "connectedComponents() expects an undirected graph in this fundamentals lab"
            );
        }

        Set<T> visited = new HashSet<>();
        List<List<T>> components = new ArrayList<>();

        for (T vertex : graph.vertices()) {
            if (visited.contains(vertex)) {
                continue;
            }

            List<T> component = new ArrayList<>();
            collectComponent(graph, vertex, visited, component);
            components.add(List.copyOf(component));
        }

        return List.copyOf(components);
    }

    private <T> void collectComponent(
            Graph<T> graph,
            T current,
            Set<T> visited,
            List<T> component
    ) {
        visited.add(current);
        component.add(current);

        for (T neighbor : graph.neighborsOf(current)) {
            if (!visited.contains(neighbor)) {
                collectComponent(graph, neighbor, visited, component);
            }
        }
    }

    /**
     * Directed-cycle detection чрез three-state DFS:
     * UNVISITED → VISITING → VISITED.
     * Edge към VISITING node е back edge и доказва cycle.
     */
    public <T> boolean hasDirectedCycle(Graph<T> graph) {
        if (!graph.directed()) {
            throw new IllegalArgumentException(
                    "hasDirectedCycle() expects a directed graph in this fundamentals lab"
            );
        }

        Map<T, VisitState> states = new HashMap<>();
        for (T vertex : graph.vertices()) {
            states.put(vertex, VisitState.UNVISITED);
        }

        for (T vertex : graph.vertices()) {
            if (states.get(vertex) == VisitState.UNVISITED
                    && hasDirectedCycle(graph, vertex, states)) {
                return true;
            }
        }

        return false;
    }

    private <T> boolean hasDirectedCycle(
            Graph<T> graph,
            T current,
            Map<T, VisitState> states
    ) {
        states.put(current, VisitState.VISITING);

        for (T neighbor : graph.neighborsOf(current)) {
            VisitState state = states.get(neighbor);

            if (state == VisitState.VISITING) {
                return true;
            }

            if (state == VisitState.UNVISITED
                    && hasDirectedCycle(graph, neighbor, states)) {
                return true;
            }
        }

        states.put(current, VisitState.VISITED);
        return false;
    }

    public <T> Set<T> reachableVertices(Graph<T> graph, T start) {
        TraversalResult<T> traversal = traverseIterative(graph, start);
        return Set.copyOf(new LinkedHashSet<>(traversal.order()));
    }

    private <T> void requireVertex(Graph<T> graph, T vertex) {
        if (!graph.vertices().contains(vertex)) {
            throw new IllegalArgumentException("unknown vertex: " + vertex);
        }
    }

    private enum VisitState {
        UNVISITED,
        VISITING,
        VISITED
    }

    private static final class MutableCounter {
        private long value;
    }

    private static final class MutableMaximum {
        private int value;
    }
}
