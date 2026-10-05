package bg.hristomanov.education.algorithms.graphalgorithms;

import bg.hristomanov.education.algorithms.graphalgorithms.routing.bad.FewestHopsRouter;
import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.BellmanFordResult;
import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.BellmanFordShortestPaths;
import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.DijkstraResult;
import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.DijkstraShortestPaths;
import bg.hristomanov.education.algorithms.graphalgorithms.weighted.WeightedDirectedGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortestPathAlgorithmsTest {

    private final DijkstraShortestPaths dijkstra = new DijkstraShortestPaths();
    private final BellmanFordShortestPaths bellmanFord = new BellmanFordShortestPaths();

    @Test
    void weightedShortestPathCanDifferFromFewestHopPath() {
        WeightedDirectedGraph<String> graph = new WeightedDirectedGraph<>();
        graph.addEdge("A", "D", 10);
        graph.addEdge("A", "B", 2);
        graph.addEdge("B", "C", 2);
        graph.addEdge("C", "D", 2);

        List<String> fewestHops = new FewestHopsRouter().route(graph, "A", "D");
        DijkstraResult<String> weighted = dijkstra.compute(graph, "A");

        assertThat(fewestHops).containsExactly("A", "D");
        assertThat(weighted.paths().pathTo("D")).containsExactly("A", "B", "C", "D");
        assertThat(weighted.paths().distanceTo("D")).isEqualTo(6);
    }

    @Test
    void dijkstraAndBellmanFordAgreeWhenAllWeightsAreNonNegative() {
        WeightedDirectedGraph<String> graph = new WeightedDirectedGraph<>();
        graph.addEdge("A", "B", 4);
        graph.addEdge("A", "C", 1);
        graph.addEdge("C", "B", 2);
        graph.addEdge("B", "D", 1);
        graph.addEdge("C", "D", 7);

        DijkstraResult<String> dijkstraResult = dijkstra.compute(graph, "A");
        BellmanFordResult<String> bellmanResult = bellmanFord.compute(graph, "A");

        assertThat(bellmanResult.reachableNegativeCycle()).isFalse();
        assertThat(dijkstraResult.paths().distanceTo("D")).isEqualTo(4);
        assertThat(bellmanResult.paths().distanceTo("D")).isEqualTo(4);
        assertThat(dijkstraResult.paths().pathTo("D")).containsExactly("A", "C", "B", "D");
    }

    @Test
    void dijkstraRejectsNegativeWeightsInsteadOfSilentlyReturningAnUnsafeAnswer() {
        WeightedDirectedGraph<String> graph = new WeightedDirectedGraph<>();
        graph.addEdge("A", "B", 5);
        graph.addEdge("A", "C", 2);
        graph.addEdge("C", "B", -10);

        assertThatThrownBy(() -> dijkstra.compute(graph, "A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");

        BellmanFordResult<String> bellmanResult = bellmanFord.compute(graph, "A");

        assertThat(bellmanResult.reachableNegativeCycle()).isFalse();
        assertThat(bellmanResult.paths().distanceTo("B")).isEqualTo(-8);
        assertThat(bellmanResult.paths().pathTo("B")).containsExactly("A", "C", "B");
    }

    @Test
    void bellmanFordDetectsReachableNegativeCycle() {
        WeightedDirectedGraph<String> graph = new WeightedDirectedGraph<>();
        graph.addEdge("A", "B", 1);
        graph.addEdge("B", "C", -3);
        graph.addEdge("C", "A", 1);

        BellmanFordResult<String> result = bellmanFord.compute(graph, "A");

        assertThat(result.reachableNegativeCycle()).isTrue();
    }

    @Test
    void bellmanFordDoesNotReportAnUnreachableNegativeCycleFromThisSource() {
        WeightedDirectedGraph<String> graph = new WeightedDirectedGraph<>();
        graph.addEdge("A", "B", 2);
        graph.addEdge("X", "Y", -3);
        graph.addEdge("Y", "X", 1);

        BellmanFordResult<String> result = bellmanFord.compute(graph, "A");

        assertThat(result.reachableNegativeCycle()).isFalse();
        assertThat(result.paths().reachable("X")).isFalse();
    }
}
