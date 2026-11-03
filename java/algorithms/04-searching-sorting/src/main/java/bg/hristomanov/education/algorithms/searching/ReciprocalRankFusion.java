package bg.hristomanov.education.algorithms.searching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Combines rankings, not raw search/embedding scores. Use document IDs scoped to one authorized tenant. */
public final class ReciprocalRankFusion {
    private ReciprocalRankFusion() { }

    public record RankedDocument<T>(T document, double score) { }

    /** rank is 1-based. Ties use first encounter across supplied ranking lists. */
    public static <T> List<RankedDocument<T>> fuse(
            List<? extends List<T>> rankings, int rankConstant, int limit) {
        Objects.requireNonNull(rankings, "rankings");
        if (rankConstant <= 0) throw new IllegalArgumentException("rankConstant must be positive");
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");

        Map<T, Double> scores = new HashMap<>();
        Map<T, Integer> firstSeen = new HashMap<>();
        int encounter = 0;
        for (List<T> ranking : rankings) {
            Objects.requireNonNull(ranking, "ranking");
            Set<T> seenHere = new HashSet<>();
            for (int index = 0; index < ranking.size(); index++) {
                T id = Objects.requireNonNull(ranking.get(index), "document ID");
                if (!seenHere.add(id)) {
                    throw new IllegalArgumentException("Duplicate document in one ranking: " + id);
                }
                firstSeen.putIfAbsent(id, encounter++);
                scores.merge(id, 1.0 / (rankConstant + (double) index + 1.0), Double::sum);
            }
        }
        List<RankedDocument<T>> output = new ArrayList<>(scores.size());
        scores.forEach((id, score) -> output.add(new RankedDocument<>(id, score)));
        output.sort(Comparator.<RankedDocument<T>>comparingDouble(RankedDocument::score)
                .reversed().thenComparingInt(r -> firstSeen.get(r.document())));
        return List.copyOf(output.subList(0, Math.min(limit, output.size())));
    }
}
