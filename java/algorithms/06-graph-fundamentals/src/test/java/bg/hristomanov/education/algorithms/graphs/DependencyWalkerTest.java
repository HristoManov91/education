package bg.hristomanov.education.algorithms.graphs;

import bg.hristomanov.education.algorithms.graphs.dependencies.bad.CycleBlindDependencyWalker;
import bg.hristomanov.education.algorithms.graphs.dependencies.good.VisitedDependencyWalker;
import bg.hristomanov.education.algorithms.graphs.representation.AdjacencyListGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyWalkerTest {

    @Test
    void visitedStateTurnsCyclicDependencyTraversalIntoFiniteWork() {
        AdjacencyListGraph<String> dependencies = new AdjacencyListGraph<>(true);
        dependencies.addEdge("api", "billing");
        dependencies.addEdge("billing", "database");
        dependencies.addEdge("database", "api");
        dependencies.addEdge("billing", "audit");

        CycleBlindDependencyWalker badWalker = new CycleBlindDependencyWalker();
        VisitedDependencyWalker goodWalker = new VisitedDependencyWalker();

        List<String> badOrder = badWalker.walk(dependencies, "api", 7);
        List<String> goodOrder = goodWalker.walk(dependencies, "api");

        assertThat(badOrder.size()).isGreaterThan(goodOrder.size());
        assertThat(badOrder).contains("api", "billing", "database", "audit");
        assertThat(goodOrder).containsExactly("api", "billing", "database", "audit");
        assertThat(goodOrder).doesNotHaveDuplicates();
        assertThat(badOrder.stream().filter("api"::equals).count()).isGreaterThan(1);
    }
}
