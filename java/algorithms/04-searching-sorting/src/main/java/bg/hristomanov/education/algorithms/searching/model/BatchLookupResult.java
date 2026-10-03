package bg.hristomanov.education.algorithms.searching.model;

import java.util.List;

public record BatchLookupResult(List<Integer> indexes, long comparisons) {
}
