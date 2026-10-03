package bg.hristomanov.education.algorithms.graphs;

import bg.hristomanov.education.algorithms.graphs.model.TraversalResult;
import bg.hristomanov.education.algorithms.graphs.representation.AdjacencyListGraph;
import bg.hristomanov.education.algorithms.graphs.traversal.DepthFirstSearch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DepthFirstSearchTest {

    private final DepthFirstSearch dfs = new DepthFirstSearch();

    @Test
    void recursiveAndIterativeDfsReachTheSameVertices() {
        AdjacencyListGraph<String> graph = new AdjacencyListGraph<>(true);
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "E");
        graph.addEdge("D", "F");
        graph.addEdge("E", "F");

        TraversalResult<String> recursive = dfs.traverseRecursive(graph, "A");
        TraversalResult<String> iterative = dfs.traverseIterative(graph, "A");

        assertThat(recursive.order()).containsExactly("A", "B", "D", "F", "C", "E");
        assertThat(iterative.order()).containsExactly("A", "B", "D", "F", "C", "E");
    }

    @Test
    void connectedComponentsFindsDisconnectedGroups() {
        AdjacencyListGraph<String> graph = new AdjacencyListGraph<>(false);
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("D", "E");
        graph.addVertex("F");

        List<List<String>> components = dfs.connectedComponents(graph);

        assertThat(components).hasSize(3);
        assertThat(components).anySatisfy(component ->
                assertThat(component).containsExactlyInAnyOrder("A", "B", "C"));
        assertThat(components).anySatisfy(component ->
                assertThat(component).containsExactlyInAnyOrder("D", "E"));
        assertThat(components).anySatisfy(component ->
                assertThat(component).containsExactly("F"));
    }

    @Test
    void directedCycleDetectionDistinguishesCyclicAndAcyclicGraphs() {
        AdjacencyListGraph<String> cyclic = new AdjacencyListGraph<>(true);
        cyclic.addEdge("A", "B");
        cyclic.addEdge("B", "C");
        cyclic.addEdge("C", "A");

        AdjacencyListGraph<String> acyclic = new AdjacencyListGraph<>(true);
        acyclic.addEdge("A", "B");
        acyclic.addEdge("B", "C");
        acyclic.addEdge("A", "C");

        assertThat(dfs.hasDirectedCycle(cyclic)).isTrue();
        assertThat(dfs.hasDirectedCycle(acyclic)).isFalse();
    }
}
