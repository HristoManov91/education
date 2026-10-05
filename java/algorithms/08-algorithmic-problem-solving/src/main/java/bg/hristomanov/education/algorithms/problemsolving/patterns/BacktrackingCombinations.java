package bg.hristomanov.education.algorithms.problemsolving.patterns;

import java.util.ArrayList;
import java.util.List;

/**
 * Backtracking mental model:
 * choose → explore → undo → choose next.
 */
public final class BacktrackingCombinations {

    public <T> List<List<T>> choose(List<T> values, int count) {
        if (count < 0 || count > values.size()) {
            throw new IllegalArgumentException("count must be in [0, values.size()]");
        }

        List<List<T>> result = new ArrayList<>();
        List<T> current = new ArrayList<>(count);

        explore(values, count, 0, current, result);
        return List.copyOf(result);
    }

    private <T> void explore(
            List<T> values,
            int targetCount,
            int start,
            List<T> current,
            List<List<T>> result
    ) {
        if (current.size() == targetCount) {
            result.add(List.copyOf(current));
            return;
        }

        int stillNeeded = targetCount - current.size();

        for (int index = start; index <= values.size() - stillNeeded; index++) {
            current.add(values.get(index));          // choose
            explore(values, targetCount, index + 1, current, result); // explore
            current.removeLast();                   // undo
        }
    }
}
