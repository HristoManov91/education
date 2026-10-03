package bg.hristomanov.education.algorithms.sorting.model;

public record SortResult(int[] values, SortMetrics metrics) {

    public SortResult {
        values = values.clone();
    }

    @Override
    public int[] values() {
        return values.clone();
    }
}
