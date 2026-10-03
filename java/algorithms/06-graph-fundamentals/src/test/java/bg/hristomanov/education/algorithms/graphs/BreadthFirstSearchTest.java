package bg.hristomanov.education.algorithms.graphs;

import bg.hristomanov.education.algorithms.graphs.model.PathResult;
import bg.hristomanov.education.algorithms.graphs.model.TraversalResult;
import bg.hristomanov.education.algorithms.graphs.representation.AdjacencyListGraph;
import bg.hristomanov.education.algorithms.graphs.traversal.BreadthFirstSearch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BreadthFirstSearchTest {

    private final BreadthFirstSearch bfs = new BreadthFirstSearch();

    @Test
    void bfsVisitsTheGraphLayerByLayerWithoutRepeatingCycleVertices() {
        AdjacencyListGraph<String> graph = cyclicUndirectedGraph();

        TraversalResult<String> result = bfs.traverse(graph, "A");

        assertThat(result.order()).containsExactly("A", "B", "C", "D", "E", "F");
        assertThat(result.order()).doesNotHaveDuplicates();
        assertThat(result.edgeInspections()).isGreaterThan(0);
    }

    @Test
    void bfsFindsMinimumNumberOfEdgesInAnUnweightedGraph() {
        AdjacencyListGraph<String> graph = new AdjacencyListGraph<>(false);
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("D", "F");
        graph.addEdge("C", "E");
        graph.addEdge("E", "F");

        PathResult<String> path = bfs.shortestPath(graph, "A", "F");

        assertThat(path.reachable()).isTrue();
        assertThat(path.edgeCount()).isEqualTo(3);
        assertThat(path.path()).startsWith("A").endsWith("F");
    }

    @Test
    void unreachableTargetProducesAnEmptyPath() {
        AdjacencyListGraph<String> graph = new AdjacencyListGraph<>(false);
        graph.addEdge("A", "B");
        graph.addVertex("X");

        PathResult<String> path = bfs.shortestPath(graph, "A", "X");

        assertThat(path.reachable()).isFalse();
        assertThat(path.path()).isEmpty();
        assertThat(path.edgeCount()).isEqualTo(-1);
    }

    private AdjacencyListGraph<String> cyclicUndirectedGraph() {
        AdjacencyListGraph<String> graph = new AdjacencyListGraph<>(false);
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        graph.addEdge("E", "C");
        graph.addEdge("E", "F");
        return graph;
    }
}
