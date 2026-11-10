package bg.hristomanov.education.gatherers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelStreamContentionLabTest {
    /**
     * A dedicated pool models a shared, saturated execution budget without changing JVM-wide commonPool state.
     * The two blockers hold both workers. That makes queued parallelStream work unable to enter the pool.
     */
    @Test
    @Timeout(15)
    void poolContentionDelaysParallelStreamButNotIndependentBoundedVirtualMapping() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        CountDownLatch blockersEntered = new CountDownLatch(2);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    blockersEntered.countDown();
                    try {
                        releaseBlockers.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(blockersEntered.await(5, TimeUnit.SECONDS)).isTrue();

            List<Integer> input = List.of(1, 2, 3, 4, 5);
            Future<List<Integer>> queued = pool.submit(
                    () -> ParallelStreamContentionLab.parallelMap(input, value -> value * 10));

            assertThatThrownBy(() -> queued.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            // This runs from the owner test thread, not inside the saturated ForkJoinPool.
            List<Integer> separatelyScheduled = ParallelStreamContentionLab.boundedVirtualMap(
                    input, 2, value -> value * 10);
            assertThat(separatelyScheduled).containsExactly(10, 20, 30, 40, 50);

            releaseBlockers.countDown();
            assertThat(queued.get(5, TimeUnit.SECONDS)).containsExactly(10, 20, 30, 40, 50);
        } finally {
            releaseBlockers.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void boundedMappingPreservesEncounterOrderAndDoesNotExceedConcurrencyBudget() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger observedMax = new AtomicInteger();
        var result = ParallelStreamContentionLab.boundedVirtualMap(
                List.of(1, 2, 3, 4, 5), 2, value -> {
                    int active = inFlight.incrementAndGet();
                    observedMax.accumulateAndGet(active, Math::max);
                    try {
                        return value * 10;
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
        assertThat(result).containsExactly(10, 20, 30, 40, 50);
        assertThat(observedMax.get()).isBetween(1, 2);
        assertThat(inFlight.get()).isZero();
    }

    @Test
    void rejectsInvalidConcurrencyBudgetAndNullArguments() {
        assertThatThrownBy(() -> ParallelStreamContentionLab.boundedVirtualMap(
                List.of(1), 0, Object::toString)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParallelStreamContentionLab.parallelMap(null, Object::toString))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ParallelStreamContentionLab.boundedVirtualMap(
                List.of(1), 2, null)).isInstanceOf(NullPointerException.class);
    }
}
