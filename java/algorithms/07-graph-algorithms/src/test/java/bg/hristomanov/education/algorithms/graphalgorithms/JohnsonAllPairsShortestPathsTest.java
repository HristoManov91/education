package bg.hristomanov.education.algorithms.graphalgorithms;

import bg.hristomanov.education.algorithms.graphalgorithms.apsp.JohnsonAllPairsShortestPaths;
import bg.hristomanov.education.algorithms.graphalgorithms.apsp.JohnsonResult;
import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.BellmanFordResult;
import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.BellmanFordShortestPaths;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedDirectedGraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JohnsonAllPairsShortestPathsTest {

    @Test
    void johnsonMatchesBellmanFordFromEverySourceWithNegativeEdgesButNoNegativeCycle() {
        WeightedDirectedGraph<String> graph = graphWithNegativeEdgesAndNoNegativeCycle();
        JohnsonResult<String> johnson = new JohnsonAllPairsShortestPaths().compute(graph);
        BellmanFordShortestPaths bellmanFord = new BellmanFordShortestPaths();

        for (String source : graph.orderedVertices()) {
            BellmanFordResult<String> expected = bellmanFord.compute(graph, source);

            assertThat(expected.reachableNegativeCycle()).isFalse();

            for (String target : graph.orderedVertices()) {
                assertThat(johnson.paths().distance(source, target))
                        .as(source + " -> " + target)
                        .isEqualTo(expected.paths().distanceTo(target));
            }
        }

        assertThat(johnson.paths().distance("A", "D")).isEqualTo(1);
        assertThat(johnson.paths().distance("C", "B")).isEqualTo(-2);
    }

    @Test
    void johnsonRejectsAnyNegativeCycleBecauseSyntheticSourceMakesAllVerticesReachable() {
        WeightedDirectedGraph<String> graph = new WeightedDirectedGraph<>();
        graph.addEdge("A", "B", 1);
        graph.addEdge("B", "C", -3);
        graph.addEdge("C", "A", 1);

        assertThatThrownBy(() -> new JohnsonAllPairsShortestPaths().compute(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative cycle");
    }

    private WeightedDirectedGraph<String> graphWithNegativeEdgesAndNoNegativeCycle() {
        WeightedDirectedGraph<String> graph = new WeightedDirectedGraph<>();
        graph.addEdge("A", "B", 4);
        graph.addEdge("A", "C", 1);
        graph.addEdge("C", "B", -2);
        graph.addEdge("B", "C", 3);
        graph.addEdge("B", "D", 2);
        graph.addEdge("C", "D", 5);
        graph.addEdge("D", "A", 3);
        return graph;
    }
}
