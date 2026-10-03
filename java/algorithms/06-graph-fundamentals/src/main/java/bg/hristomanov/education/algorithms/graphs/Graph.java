package bg.hristomanov.education.algorithms.graphs;

import java.util.List;
import java.util.Set;

/**
 * Минималният contract, който BFS/DFS лабораториите очакват от graph representation.
 */
public interface Graph<T> {

    Set<T> vertices();

    List<T> neighborsOf(T vertex);

    boolean containsEdge(T from, T to);

    boolean directed();
}
