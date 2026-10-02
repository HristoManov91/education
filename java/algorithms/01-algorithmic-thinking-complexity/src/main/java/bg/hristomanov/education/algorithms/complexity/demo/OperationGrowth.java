package bg.hristomanov.education.algorithms.complexity.demo;

/**
 * Детерминистични броячи на logical steps.
 *
 * <p>Това НЕ е benchmark. Използваме го, за да доказваме growth shape без шум
 * от JIT, GC, CPU cache, OS scheduling и други runtime фактори.</p>
 */
public final class OperationGrowth {

    private OperationGrowth() {
    }

    public static long linearSteps(int inputSize) {
        requireNonNegative(inputSize);
        long steps = 0;
        for (int i = 0; i < inputSize; i++) {
            steps++;
        }
        return steps;
    }

    public static long quadraticSteps(int inputSize) {
        requireNonNegative(inputSize);
        long steps = 0;
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                steps++;
            }
        }
        return steps;
    }

    public static int halvingSteps(int inputSize) {
        if (inputSize < 1) {
            throw new IllegalArgumentException("inputSize must be >= 1");
        }

        int remaining = inputSize;
        int steps = 0;
        while (remaining > 1) {
            remaining = (remaining + 1) / 2;
            steps++;
        }
        return steps;
    }

    private static void requireNonNegative(int inputSize) {
        if (inputSize < 0) {
            throw new IllegalArgumentException("inputSize must be >= 0");
        }
    }
}
