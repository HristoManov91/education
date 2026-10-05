package bg.hristomanov.education.algorithms.problemsolving.patterns;

import java.util.Optional;

/**
 * Two-pointers пример върху ascending-sorted array.
 *
 * <p>Invariant-ът е, че ако сумата е твърде малка, местим left надясно;
 * ако е твърде голяма, местим right наляво. Така отхвърляме кандидати,
 * вместо да проверяваме всички O(n²) двойки.</p>
 */
public final class TwoPointersPairSum {

    public Optional<Pair> find(int[] sortedValues, int target) {
        int left = 0;
        int right = sortedValues.length - 1;

        while (left < right) {
            long sum = (long) sortedValues[left] + sortedValues[right];

            if (sum == target) {
                return Optional.of(
                        new Pair(left, right, sortedValues[left], sortedValues[right])
                );
            }

            if (sum < target) {
                left++;
            } else {
                right--;
            }
        }

        return Optional.empty();
    }

    public record Pair(
            int leftIndex,
            int rightIndex,
            int leftValue,
            int rightValue
    ) {
    }
}
