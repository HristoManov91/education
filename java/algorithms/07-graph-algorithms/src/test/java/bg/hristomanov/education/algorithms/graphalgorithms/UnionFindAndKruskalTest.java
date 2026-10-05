package bg.hristomanov.education.algorithms.graphalgorithms;

import bg.hristomanov.education.algorithms.graphalgorithms.mst.KruskalMinimumSpanningTree;
import bg.hristomanov.education.algorithms.graphalgorithms.mst.MstResult;
import bg.hristomanov.education.algorithms.graphalgorithms.mst.WeightedUndirectedGraph;
import bg.hristomanov.education.algorithms.graphalgorithms.unionfind.UnionFind;
import bg.hristomanov.education.algorithms.graphalgorithms.unionfind.UnionFindMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnionFindAndKruskalTest {

    @Test
    void unionFindMaintainsConnectivityAndCompressesPaths() {
        UnionFind<String> unionFind =
                new UnionFind<>(List.of("A", "B", "C", "D", "E", "F", "G", "H"));

        unionFind.union("A", "B");
        unionFind.union("C", "D");
        unionFind.union("A", "C");

        unionFind.union("E", "F");
        unionFind.union("G", "H");
        unionFind.union("E", "G");

        unionFind.union("A", "E");

        unionFind.resetMetrics();

        assertThat(unionFind.connected("D", "H")).isTrue();
        assertThat(unionFind.componentSize("D")).isEqualTo(8);

        UnionFindMetrics metrics = unionFind.metrics();
        assertThat(metrics.parentTraversals()).isGreaterThan(0);
    }

    @Test
    void kruskalBuildsMinimumSpanningTreeAndSkipsCycleEdges() {
        WeightedUndirectedGraph<String> graph = new WeightedUndirectedGraph<>();
        graph.addEdge("A", "B", 1);
        graph.addEdge("B", "C", 2);
        graph.addEdge("A", "C", 4);
        graph.addEdge("C", "D", 1);
        graph.addEdge("B", "D", 5);

        MstResult<String> result = new KruskalMinimumSpanningTree().compute(graph);

        assertThat(result.spanning()).isTrue();
        assertThat(result.edges()).hasSize(3);
        assertThat(result.totalWeight()).isEqualTo(4);
        assertThat(result.edges())
                .extracting(edge -> edge.weight())
                .containsExactlyInAnyOrder(1L, 1L, 2L);
    }

    @Test
    void disconnectedGraphProducesMinimumSpanningForestButNotASpanningTree() {
        WeightedUndirectedGraph<String> graph = new WeightedUndirectedGraph<>();
        graph.addEdge("A", "B", 1);
        graph.addEdge("C", "D", 2);

        MstResult<String> result = new KruskalMinimumSpanningTree().compute(graph);

        assertThat(result.spanning()).isFalse();
        assertThat(result.edges()).hasSize(2);
        assertThat(result.totalWeight()).isEqualTo(3);
    }
}
