package bg.hristomanov.education.algorithms.problemsolving.dp.advanced;

/**
 * Pseudopolynomial DP:
 * reachable[sum] пази дали target sum може да се построи от processed elements.
 *
 * <p>Time/space зависят от numeric target, не само от броя input elements.</p>
 */
public final class SubsetSumDynamicProgramming {

    public Result canReach(int[] values, int target) {
        if (target < 0) {
            throw new IllegalArgumentException("target must be >= 0");
        }

        boolean[] reachable = new boolean[target + 1];
        reachable[0] = true;
        long transitionChecks = 0;

        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "this educational formulation supports non-negative values only"
                );
            }

            for (int sum = target; sum >= value; sum--) {
                transitionChecks++;

                if (reachable[sum - value]) {
                    reachable[sum] = true;
                }
            }
        }

        return new Result(
                reachable[target],
                target + 1L,
                transitionChecks
        );
    }

    public record Result(
            boolean reachable,
            long stateSlots,
            long transitionChecks
    ) {
    }
}
