package bg.hristomanov.education.algorithms.graphalgorithms.shortestpath;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result за single-source shortest paths.
 */
public record ShortestPathResult<T>(
        T source,
        Map<T, Long> distances,
        Map<T, T> predecessors
) {

    public ShortestPathResult {
        Objects.requireNonNull(source, "source");
        distances = Map.copyOf(new LinkedHashMap<>(distances));
        predecessors = Map.copyOf(new LinkedHashMap<>(predecessors));
    }

    public boolean reachable(T target) {
        return distanceTo(target) != ShortestPathMath.INFINITY;
    }

    public long distanceTo(T target) {
        Long distance = distances.get(target);
        if (distance == null) {
            throw new IllegalArgumentException("unknown vertex: " + target);
        }

        return distance;
    }

    public List<T> pathTo(T target) {
        if (!reachable(target)) {
            return List.of();
        }

        LinkedList<T> path = new LinkedList<>();
        T current = target;

        while (!current.equals(source)) {
            path.addFirst(current);
            current = predecessors.get(current);

            if (current == null) {
                throw new IllegalStateException(
                        "reachable target has no predecessor chain to source"
                );
            }
        }

        path.addFirst(source);
        return List.copyOf(path);
    }

    public static long infinity() {
        return ShortestPathMath.INFINITY;
    }
}
