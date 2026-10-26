package bg.hristomanov.education.gatherers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class StreamGatheringExercisesTest {

    @Test
    void fixedWindowsIncludeShortFinalBatch() {
        assertThat(StreamGatheringExercises.batches(List.of(1, 2, 3, 4, 5), 2))
                .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(StreamGatheringExercises.batches(List.of(), 3)).isEmpty();
    }

    @Test
    void fixedWindowsAreUnmodifiable() {
        var windows = StreamGatheringExercises.batches(List.of(1, 2, 3), 2);
        assertThatThrownBy(() -> windows.getFirst().add(99))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void slidingWindowsOverlapAndHandleSmallerInput() {
        assertThat(StreamGatheringExercises.slidingWindows(List.of(1, 2, 3, 4), 3))
                .containsExactly(List.of(1, 2, 3), List.of(2, 3, 4));
        assertThat(StreamGatheringExercises.slidingWindows(List.of(1, 2), 3))
                .containsExactly(List.of(1, 2));
    }

    @Test
    void scanEmitsEachPrefixButFoldOnlyFinalResult() {
        assertThat(StreamGatheringExercises.runningTotals(List.of(1, 2, 3, 4)))
                .containsExactly(1, 3, 6, 10);
        assertThat(StreamGatheringExercises.foldedText(List.of("A", "B", "C")))
                .isEqualTo("ABC");
        assertThat(StreamGatheringExercises.prefixes(List.of(1, 2, 3))
                .filter(sum -> sum > 2).toList()).containsExactly(3, 6);
    }

    @Test
    void overflowRemainsVisibleRatherThanSilentlyWrapping() {
        assertThatThrownBy(() -> StreamGatheringExercises.runningTotals(
                List.of(Integer.MAX_VALUE, 1))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void customDistinctByEmitsFirstKeyAndCanComposeWithWindowing() {
        var input = List.of("ant", "bear", "cat", "door", "eagle");
        assertThat(input.stream().gather(
                StreamGatheringExercises.distinctBy(String::length)).toList())
                .containsExactly("ant", "bear", "eagle");
        assertThat(StreamGatheringExercises.firstPerLengthInBatches(input, 2))
                .containsExactly(List.of("ant", "bear"), List.of("eagle"));
    }

    @Test
    void eachStreamEvaluationGetsIndependentGathererState() {
        var gatherer = StreamGatheringExercises.distinctBy(String::length);
        assertThat(List.of("cat", "dog", "mouse").stream().gather(gatherer).toList())
                .containsExactly("cat", "mouse");
        assertThat(List.of("cat", "dog", "mouse").stream().gather(gatherer).toList())
                .containsExactly("cat", "mouse");
    }

    @Test
    void mapConcurrentPreservesOrderAndNeverExceedsBudget() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        var output = StreamGatheringExercises.orderedConcurrentMap(
                List.of("a", "b", "c", "d", "e"), 2, value -> {
                    int active = inFlight.incrementAndGet();
                    maximum.accumulateAndGet(active, Math::max);
                    try {
                        return value.toUpperCase();
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
        assertThat(output).containsExactly("A", "B", "C", "D", "E");
        assertThat(maximum.get()).isBetween(1, 2);
        assertThat(inFlight.get()).isZero();
    }

    @Test
    void illegalWindowAndConcurrencyBudgetsAreRejected() {
        assertThatThrownBy(() -> StreamGatheringExercises.batches(List.of(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StreamGatheringExercises.orderedConcurrentMap(
                List.of(1), 0, Object::toString))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
