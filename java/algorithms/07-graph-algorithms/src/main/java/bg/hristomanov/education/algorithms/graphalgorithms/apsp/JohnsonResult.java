package bg.hristomanov.education.algorithms.graphalgorithms.apsp;

import java.util.Map;

public record JohnsonResult<T>(
        AllPairsShortestPathResult<T> paths,
        Map<T, Long> potentials
) {
    public JohnsonResult {
        potentials = Map.copyOf(potentials);
    }
}
