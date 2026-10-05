package bg.hristomanov.education.algorithms.graphalgorithms.apsp;

import bg.hristomanov.education.algorithms.graphalgorithms.shortestpath.ShortestPathResult;

import java.util.LinkedHashMap;
import java.util.Map;

public record AllPairsShortestPathResult<T>(
        Map<T, Map<T, Long>> distances
) {

    public AllPairsShortestPathResult {
        Map<T, Map<T, Long>> copied = new LinkedHashMap<>();

        for (Map.Entry<T, Map<T, Long>> entry : distances.entrySet()) {
            copied.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }

        distances = Map.copyOf(copied);
    }

    public long distance(T from, T to) {
        Map<T, Long> row = distances.get(from);
        if (row == null || !row.containsKey(to)) {
            throw new IllegalArgumentException("unknown vertex pair: " + from + " -> " + to);
        }

        return row.get(to);
    }

    public boolean reachable(T from, T to) {
        return distance(from, to) != ShortestPathResult.infinity();
    }
}
