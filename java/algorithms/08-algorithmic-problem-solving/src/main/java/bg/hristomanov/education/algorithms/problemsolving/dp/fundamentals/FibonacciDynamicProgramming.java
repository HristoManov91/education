package bg.hristomanov.education.algorithms.problemsolving.dp.fundamentals;

import java.util.Arrays;

/**
 * Малък DP microscope: една и съща recurrence през naive recursion,
 * memoization и tabulation.
 */
public final class FibonacciDynamicProgramming {

    public Result naive(int n) {
        requireSupported(n);
        Counter counter = new Counter();
        long value = naive(n, counter);
        return new Result(value, counter.value, n + 1);
    }

    private long naive(int n, Counter counter) {
        counter.value++;

        if (n <= 1) {
            return n;
        }

        return naive(n - 1, counter) + naive(n - 2, counter);
    }

    public Result memoized(int n) {
        requireSupported(n);

        long[] memo = new long[n + 1];
        Arrays.fill(memo, -1);
        Counter counter = new Counter();

        long value = memoized(n, memo, counter);
        return new Result(value, counter.value, n + 1);
    }

    private long memoized(int n, long[] memo, Counter counter) {
        counter.value++;

        if (n <= 1) {
            return n;
        }

        if (memo[n] >= 0) {
            return memo[n];
        }

        memo[n] = memoized(n - 1, memo, counter) + memoized(n - 2, memo, counter);
        return memo[n];
    }

    public Result tabulated(int n) {
        requireSupported(n);

        if (n <= 1) {
            return new Result(n, 1, n + 1);
        }

        long previous = 0;
        long current = 1;
        long evaluatedStates = 2;

        for (int state = 2; state <= n; state++) {
            long next = previous + current;
            previous = current;
            current = next;
            evaluatedStates++;
        }

        return new Result(current, evaluatedStates, n + 1);
    }

    private void requireSupported(int n) {
        if (n < 0 || n > 92) {
            throw new IllegalArgumentException("n must be in [0, 92] for long results");
        }
    }

    public record Result(
            long value,
            long evaluationsOrCalls,
            long possibleStateCount
    ) {
    }

    private static final class Counter {
        private long value;
    }
}
