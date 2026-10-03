package bg.hristomanov.education.algorithms.graphs.dependencies.good;

import bg.hristomanov.education.algorithms.graphs.Graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DFS dependency walker с ownership върху visited state-а за конкретния traversal.
 */
public final class VisitedDependencyWalker {

    public List<String> walk(Graph<String> graph, String start) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        walk(graph, start, visited, order);
        return List.copyOf(order);
    }

    private void walk(
            Graph<String> graph,
            String current,
            Set<String> visited,
            List<String> order
    ) {
        if (!visited.add(current)) {
            return;
        }

        order.add(current);

        for (String dependency : graph.neighborsOf(current)) {
            walk(graph, dependency, visited, order);
        }
    }
}
