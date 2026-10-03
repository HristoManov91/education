package bg.hristomanov.education.algorithms.graphs;

import bg.hristomanov.education.algorithms.graphs.representation.AdjacencyListGraph;
import bg.hristomanov.education.algorithms.graphs.representation.AdjacencyMatrixGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphRepresentationTest {

    @Test
    void listAndMatrixCanRepresentTheSameDirectedRelationships() {
        List<String> vertices = List.of("A", "B", "C", "D");

        AdjacencyListGraph<String> listGraph = new AdjacencyListGraph<>(true);
        for (String vertex : vertices) {
            listGraph.addVertex(vertex);
        }

        AdjacencyMatrixGraph<String> matrixGraph =
                new AdjacencyMatrixGraph<>(vertices, true);

        addSameEdges(listGraph, matrixGraph);

        assertThat(listGraph.neighborsOf("A")).containsExactly("B", "C");
        assertThat(matrixGraph.neighborsOf("A")).containsExactly("B", "C");
        assertThat(listGraph.containsEdge("C", "D")).isTrue();
        assertThat(matrixGraph.containsEdge("C", "D")).isTrue();
        assertThat(listGraph.containsEdge("D", "A")).isFalse();
        assertThat(matrixGraph.containsEdge("D", "A")).isFalse();
    }

    @Test
    void sparseGraphMakesTheStorageTradeOffVisible() {
        List<Integer> vertices = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            vertices.add(i);
        }

        AdjacencyListGraph<Integer> listGraph = new AdjacencyListGraph<>(true);
        for (Integer vertex : vertices) {
            listGraph.addVertex(vertex);
        }

        AdjacencyMatrixGraph<Integer> matrixGraph =
                new AdjacencyMatrixGraph<>(vertices, true);

        for (int i = 0; i < 99; i++) {
            listGraph.addEdge(i, i + 1);
            matrixGraph.addEdge(i, i + 1);
        }

        assertThat(listGraph.storedEdgeReferences()).isEqualTo(99);
        assertThat(matrixGraph.storedTrueCells()).isEqualTo(99);
        assertThat(matrixGraph.storageCellCount()).isEqualTo(10_000);
    }

    @Test
    void undirectedEdgeIsVisibleFromBothVertices() {
        AdjacencyListGraph<String> graph = new AdjacencyListGraph<>(false);

        graph.addEdge("Sofia", "Plovdiv");

        assertThat(graph.containsEdge("Sofia", "Plovdiv")).isTrue();
        assertThat(graph.containsEdge("Plovdiv", "Sofia")).isTrue();
    }

    private void addSameEdges(
            AdjacencyListGraph<String> listGraph,
            AdjacencyMatrixGraph<String> matrixGraph
    ) {
        listGraph.addEdge("A", "B");
        listGraph.addEdge("A", "C");
        listGraph.addEdge("B", "D");
        listGraph.addEdge("C", "D");

        matrixGraph.addEdge("A", "B");
        matrixGraph.addEdge("A", "C");
        matrixGraph.addEdge("B", "D");
        matrixGraph.addEdge("C", "D");
    }
}
