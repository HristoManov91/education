package bg.hristomanov.education.algorithms.problemsolving.planning.bad;

import bg.hristomanov.education.algorithms.problemsolving.planning.BatchPlanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Naive recursion за minimum-batch problem.
 *
 * <p>Един и същ remaining target се решава многократно през различни recursion
 * branches. Това е точно repeated subproblem shape-ът, който DP премахва.</p>
 */
public final class RecursiveMinimumBatchPlanner {

    public BatchPlanResult plan(int targetUnits, int[] allowedBatchSizes) {
        validate(targetUnits, allowedBatchSizes);

        Counter counter = new Counter();
        SearchResult result = solve(targetUnits, allowedBatchSizes, counter);

        if (!result.possible()) {
            return new BatchPlanResult(-1, List.of(), counter.calls);
        }

        return new BatchPlanResult(
                result.count(),
                result.batchSizes(),
                counter.calls
        );
    }

    private SearchResult solve(
            int remaining,
            int[] allowedBatchSizes,
            Counter counter
    ) {
        counter.calls++;

        if (remaining == 0) {
            return new SearchResult(0, List.of());
        }

        if (remaining < 0) {
            return SearchResult.impossible();
        }

        SearchResult best = SearchResult.impossible();

        for (int batchSize : allowedBatchSizes) {
            SearchResult subproblem = solve(
                    remaining - batchSize,
                    allowedBatchSizes,
                    counter
            );

            if (!subproblem.possible()) {
                continue;
            }

            int candidateCount = subproblem.count() + 1;
            if (!best.possible() || candidateCount < best.count()) {
                List<Integer> candidate = new ArrayList<>(subproblem.batchSizes());
                candidate.add(batchSize);
                best = new SearchResult(candidateCount, List.copyOf(candidate));
            }
        }

        return best;
    }

    private void validate(int targetUnits, int[] allowedBatchSizes) {
        if (targetUnits < 0) {
            throw new IllegalArgumentException("targetUnits must be >= 0");
        }
        for (int batchSize : allowedBatchSizes) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batch sizes must be > 0");
            }
        }
    }

    private record SearchResult(int count, List<Integer> batchSizes) {
        private static SearchResult impossible() {
            return new SearchResult(-1, List.of());
        }

        private boolean possible() {
            return count >= 0;
        }
    }

    private static final class Counter {
        private long calls;
    }
}
