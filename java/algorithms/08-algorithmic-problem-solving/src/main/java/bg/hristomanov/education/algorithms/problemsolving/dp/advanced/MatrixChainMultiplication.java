package bg.hristomanov.education.algorithms.problemsolving.dp.advanced;

/**
 * Interval DP example.
 *
 * <p>dimensions = [d0, d1, d2, ...] описва matrices:
 * A1=d0×d1, A2=d1×d2, ...</p>
 */
public final class MatrixChainMultiplication {

    public Result minimumScalarMultiplications(int[] dimensions) {
        if (dimensions.length < 2) {
            throw new IllegalArgumentException("at least one matrix is required");
        }
        for (int dimension : dimensions) {
            if (dimension <= 0) {
                throw new IllegalArgumentException("matrix dimensions must be > 0");
            }
        }

        int matrixCount = dimensions.length - 1;
        if (matrixCount <= 1) {
            return new Result(0, matrixCount * matrixCount, 0);
        }

        long[][] cost = new long[matrixCount][matrixCount];
        long transitionChecks = 0;

        for (int chainLength = 2; chainLength <= matrixCount; chainLength++) {
            for (int left = 0; left + chainLength - 1 < matrixCount; left++) {
                int right = left + chainLength - 1;
                cost[left][right] = Long.MAX_VALUE;

                for (int split = left; split < right; split++) {
                    transitionChecks++;

                    long multiplicationCost =
                            (long) dimensions[left]
                                    * dimensions[split + 1]
                                    * dimensions[right + 1];

                    long candidate = Math.addExact(
                            Math.addExact(cost[left][split], cost[split + 1][right]),
                            multiplicationCost
                    );

                    cost[left][right] = Math.min(cost[left][right], candidate);
                }
            }
        }

        return new Result(
                cost[0][matrixCount - 1],
                (long) matrixCount * matrixCount,
                transitionChecks
        );
    }

    public record Result(
            long minimumScalarMultiplications,
            long stateSlots,
            long transitionChecks
    ) {
    }
}
