package bg.hristomanov.education.algorithms.graphalgorithms.routing.bad;

import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedDirectedGraph;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedEdge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Нарочно неподходящ router за weighted graph: минимизира броя edges, не total weight.
 *
 * <p>Това е BFS semantics от предишния модул. Той е коректен за minimum hops,
 * но не и за minimum weighted cost.</p>
 */
public final class FewestHopsRouter {

    public <T> List<T> route(WeightedDirectedGraph<T> graph, T source, T target) {
        if (!graph.vertices().contains(source) || !graph.vertices().contains(target)) {
            throw new IllegalArgumentException("source/target must belong to graph");
        }

        Set<T> visited = new HashSet<>();
        Map<T, T> predecessor = new HashMap<>();
        Deque<T> queue = new ArrayDeque<>();

        visited.add(source);
        queue.addLast(source);

        while (!queue.isEmpty()) {
            T current = queue.removeFirst();

            if (current.equals(target)) {
                return reconstruct(predecessor, source, target);
            }

            for (WeightedEdge<T> edge : graph.outgoingEdgesOf(current)) {
                if (visited.add(edge.to())) {
                    predecessor.put(edge.to(), current);
                    queue.addLast(edge.to());
                }
            }
        }

        return List.of();
    }

    private <T> List<T> reconstruct(
            Map<T, T> predecessor,
            T source,
            T target
    ) {
        LinkedList<T> path = new LinkedList<>();
        T current = target;

        while (!current.equals(source)) {
            path.addFirst(current);
            current = predecessor.get(current);
        }

        path.addFirst(source);
        return List.copyOf(path);
    }
}
