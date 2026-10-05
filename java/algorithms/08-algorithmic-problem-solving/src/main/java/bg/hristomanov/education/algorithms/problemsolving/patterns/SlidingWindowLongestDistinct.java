package bg.hristomanov.education.algorithms.problemsolving.patterns;

import java.util.HashMap;
import java.util.Map;

/**
 * Dynamic sliding window:
 * expand right → ако invariant-ът се наруши → shrink left.
 */
public final class SlidingWindowLongestDistinct {

    public Window longestSubstringWithAtMostKDistinct(String text, int maxDistinct) {
        if (maxDistinct < 0) {
            throw new IllegalArgumentException("maxDistinct must be >= 0");
        }
        if (text.isEmpty() || maxDistinct == 0) {
            return new Window(0, 0);
        }

        Map<Character, Integer> counts = new HashMap<>();
        int left = 0;
        int bestStart = 0;
        int bestEndExclusive = 0;

        for (int right = 0; right < text.length(); right++) {
            char added = text.charAt(right);
            counts.merge(added, 1, Integer::sum);

            while (counts.size() > maxDistinct) {
                char removed = text.charAt(left);
                int remaining = counts.get(removed) - 1;

                if (remaining == 0) {
                    counts.remove(removed);
                } else {
                    counts.put(removed, remaining);
                }

                left++;
            }

            int endExclusive = right + 1;
            if (endExclusive - left > bestEndExclusive - bestStart) {
                bestStart = left;
                bestEndExclusive = endExclusive;
            }
        }

        return new Window(bestStart, bestEndExclusive);
    }

    public record Window(int startInclusive, int endExclusive) {

        public int length() {
            return endExclusive - startInclusive;
        }

        public String extractFrom(String source) {
            return source.substring(startInclusive, endExclusive);
        }
    }
}
