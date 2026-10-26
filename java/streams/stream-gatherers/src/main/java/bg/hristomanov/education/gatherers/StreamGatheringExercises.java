package bg.hristomanov.education.gatherers;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

/**
 * JDK 25 examples (Gatherers finalized in Java 24).
 *
 * A Gatherer transforms a stream at an intermediate stage. The default
 * combiner for custom sequential gatherers prevents unsafe parallel state merges.
 */
public final class StreamGatheringExercises {

    private StreamGatheringExercises() {
    }

    public static List<List<Integer>> batches(List<Integer> input, int batchSize) {
        return input.stream().gather(Gatherers.windowFixed(batchSize)).toList();
    }

    public static List<List<Integer>> slidingWindows(List<Integer> input, int windowSize) {
        return input.stream().gather(Gatherers.windowSliding(windowSize)).toList();
    }

    public static List<Integer> runningTotals(List<Integer> input) {
        return input.stream()
                .gather(Gatherers.scan(() -> 0, (total, value) -> Math.addExact(total, value)))
                .toList();
    }

    /** A fold emits one final value; unlike scan it does not emit prefixes. */
    public static String foldedText(List<String> input) {
        return input.stream()
                .gather(Gatherers.fold(() -> "", (result, element) -> result + element))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Executes mapper on virtual threads with a maximum concurrency budget,
     * preserving encounter order. Side effects must be idempotent if retried by
     * the caller; mapConcurrent does not make remote operations transactional.
     */
    public static <T, R> List<R> orderedConcurrentMap(
            List<T> input, int maxConcurrency, Function<T, R> mapper) {
        return input.stream().gather(Gatherers.mapConcurrent(maxConcurrency, mapper)).toList();
    }

    /**
     * Emits only the first element for each classifier key. The fresh HashSet
     * is owned by one gather evaluation and is NOT a shared static state.
     *
     * This has no combiner: even on a parallel stream the gatherer is
     * sequential, preserving first-seen encounter semantics.
     */
    public static <T, K> Gatherer<T, ?, T> distinctBy(
            Function<? super T, ? extends K> classifier) {
        Objects.requireNonNull(classifier, "classifier");
        return Gatherer.<T, Set<K>, T>ofSequential(
                HashSet::new,
                (seen, value, downstream) -> {
                    if (seen.add(classifier.apply(value))) {
                        return downstream.push(value);
                    }
                    return true;
                });
    }

    public static List<List<String>> firstPerLengthInBatches(List<String> words, int batchSize) {
        return words.stream()
                .gather(distinctBy(String::length))
                .gather(Gatherers.windowFixed(batchSize))
                .toList();
    }

    public static Stream<Integer> prefixes(List<Integer> input) {
        // The caller may add other intermediate operations before terminal
        // collection. This is impossible with a terminal Collector.
        return input.stream().gather(Gatherers.scan(
                () -> 0, (total, next) -> Math.addExact(total, next)));
    }
}
