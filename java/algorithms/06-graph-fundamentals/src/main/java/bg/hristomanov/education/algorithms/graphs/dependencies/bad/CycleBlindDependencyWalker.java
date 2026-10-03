package bg.hristomanov.education.algorithms.graphs.dependencies.bad;

import bg.hristomanov.education.algorithms.graphs.Graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Реалистичен naive DFS: следва dependency edges рекурсивно, но няма visited set.
 *
 * <p>depthLimit съществува само за да държи учебния bad example безопасен.
 * Без него cyclic dependency може да доведе до безкрайна рекурсия / StackOverflowError.</p>
 */
public final class CycleBlindDependencyWalker {

    public List<String> walk(Graph<String> graph, String start, int depthLimit) {
        if (depthLimit < 0) {
            throw new IllegalArgumentException("depthLimit must be >= 0");
        }

        List<String> order = new ArrayList<>();
        walk(graph, start, 0, depthLimit, order);
        return List.copyOf(order);
    }

    private void walk(
            Graph<String> graph,
            String current,
            int depth,
            int depthLimit,
            List<String> order
    ) {
        order.add(current);

        if (depth == depthLimit) {
            return;
        }

        for (String dependency : graph.neighborsOf(current)) {
            walk(graph, dependency, depth + 1, depthLimit, order);
        }
    }
}
