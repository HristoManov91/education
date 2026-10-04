package bg.hristomanov.education.algorithms.graphalgorithms.weighted;

public record WeightedEdge<T>(
        T from,
        T to,
        long weight
) {
}
