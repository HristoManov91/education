package bg.hristomanov.education.algorithms.trees.heap;

public record HeapMetrics(
        long comparisons,
        long swaps,
        long resizeCopies,
        long linearSearchChecks
) {
}
