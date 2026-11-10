package bg.hristomanov.education.gatherers;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Gatherers;

/** Two approaches to independent mapping; neither provides retry or external side-effect isolation. */
public final class ParallelStreamContentionLab {
    private ParallelStreamContentionLab() { }

    /** Parallel streams normally share a ForkJoinPool; the caller's execution context matters. */
    public static <T, R> List<R> parallelMap(List<T> inputs, Function<T, R> mapper) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(mapper, "mapper");
        return inputs.parallelStream().map(mapper).toList();
    }

    /** Virtual-thread workers with explicit concurrency budget; suitable for bounded, blocking I/O. */
    public static <T, R> List<R> boundedVirtualMap(
            List<T> inputs, int maxConcurrency, Function<T, R> mapper) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(mapper, "mapper");
        return inputs.stream().gather(Gatherers.mapConcurrent(maxConcurrency, mapper)).toList();
    }
}
