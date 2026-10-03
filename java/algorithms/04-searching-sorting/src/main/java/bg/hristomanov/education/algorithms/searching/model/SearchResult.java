package bg.hristomanov.education.algorithms.searching.model;

public record SearchResult(int index, long comparisons) {

    public boolean found() {
        return index >= 0;
    }
}
