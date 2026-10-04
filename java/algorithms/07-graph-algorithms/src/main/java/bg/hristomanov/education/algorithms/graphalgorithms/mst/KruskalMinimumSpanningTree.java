package bg.hristomanov.education.algorithms.graphalgorithms.mst;

import bg.hristomanov.education.algorithms.graphalgorithms.unionfind.UnionFind;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedEdge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Kruskal: сортира edges по weight и добавя edge само ако не затваря cycle.
 *
 * <p>Union-Find дава евтин connectivity check между endpoints.</p>
 */
public final class KruskalMinimumSpanningTree {

    public <T> MstResult<T> compute(WeightedUndirectedGraph<T> graph) {
        List<WeightedEdge<T>> sortedEdges = new ArrayList<>(graph.edges());
        sortedEdges.sort(Comparator.comparingLong(WeightedEdge::weight));

        UnionFind<T> unionFind = new UnionFind<>(graph.vertices());
        List<WeightedEdge<T>> selected = new ArrayList<>();
        long totalWeight = 0;

        for (WeightedEdge<T> edge : sortedEdges) {
            if (!unionFind.union(edge.from(), edge.to())) {
                continue;
            }

            selected.add(edge);
            totalWeight = Math.addExact(totalWeight, edge.weight());

            if (selected.size() == Math.max(0, graph.vertexCount() - 1)) {
                break;
            }
        }

        boolean spanning =
                graph.vertexCount() <= 1 || selected.size() == graph.vertexCount() - 1;

        return new MstResult<>(
                List.copyOf(selected),
                totalWeight,
                spanning,
                unionFind.metrics()
        );
    }
}
