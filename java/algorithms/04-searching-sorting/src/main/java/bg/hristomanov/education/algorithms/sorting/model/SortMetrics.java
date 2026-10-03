package bg.hristomanov.education.algorithms.sorting.model;

public record SortMetrics(
        long comparisons,
        long writes,
        long auxiliarySlots
) {
}
