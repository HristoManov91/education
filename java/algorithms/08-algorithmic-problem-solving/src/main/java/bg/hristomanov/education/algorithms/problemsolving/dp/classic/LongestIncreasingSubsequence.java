package bg.hristomanov.education.algorithms.problemsolving.dp.classic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * O(n²) DP formulation:
 * dp[i] = най-дългата increasing subsequence, която завършва точно на i.
 */
public final class LongestIncreasingSubsequence {

    public Result solve(int[] values) {
        if (values.length == 0) {
            return new Result(0, List.of(), 0);
        }

        int[] length = new int[values.length];
        int[] predecessor = new int[values.length];
        long comparisons = 0;

        for (int i = 0; i < values.length; i++) {
            length[i] = 1;
            predecessor[i] = -1;

            for (int j = 0; j < i; j++) {
                comparisons++;

                if (values[j] < values[i] && length[j] + 1 > length[i]) {
                    length[i] = length[j] + 1;
                    predecessor[i] = j;
                }
            }
        }

        int bestIndex = 0;
        for (int i = 1; i < values.length; i++) {
            if (length[i] > length[bestIndex]) {
                bestIndex = i;
            }
        }

        List<Integer> sequence = new ArrayList<>();
        for (int index = bestIndex; index >= 0; index = predecessor[index]) {
            sequence.add(values[index]);

            if (predecessor[index] < 0) {
                break;
            }
        }

        Collections.reverse(sequence);
        return new Result(length[bestIndex], List.copyOf(sequence), comparisons);
    }

    public record Result(
            int length,
            List<Integer> sequence,
            long transitionChecks
    ) {
    }
}
