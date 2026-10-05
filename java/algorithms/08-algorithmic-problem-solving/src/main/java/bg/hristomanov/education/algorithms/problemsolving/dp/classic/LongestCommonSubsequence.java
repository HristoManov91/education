package bg.hristomanov.education.algorithms.problemsolving.dp.classic;

/**
 * 2D DP: state (i, j) означава най-дългата common subsequence за
 * suffix-ите first[i..] и second[j..].
 */
public final class LongestCommonSubsequence {

    public Result solve(String first, String second) {
        int[][] dp = new int[first.length() + 1][second.length() + 1];

        for (int i = first.length() - 1; i >= 0; i--) {
            for (int j = second.length() - 1; j >= 0; j--) {
                if (first.charAt(i) == second.charAt(j)) {
                    dp[i][j] = 1 + dp[i + 1][j + 1];
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        StringBuilder sequence = new StringBuilder();
        int i = 0;
        int j = 0;

        while (i < first.length() && j < second.length()) {
            if (first.charAt(i) == second.charAt(j)) {
                sequence.append(first.charAt(i));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }

        long stateCount = (long) (first.length() + 1) * (second.length() + 1);
        return new Result(dp[0][0], sequence.toString(), stateCount);
    }

    public record Result(
            int length,
            String sequence,
            long stateCount
    ) {
    }
}
