package bg.hristomanov.education.reliability.externalapi;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoadSheddingQueueTest {

    @Test
    void expiredPeriodicWorkDoesNotBlockFreshCriticalWork() {
        MutableClock clock = new MutableClock();
        LoadSheddingQueue queue = new LoadSheddingQueue(3, clock);
        List<String> executed = new ArrayList<>();

        assertThat(queue.enqueue(refresh("old-a", clock, Duration.ofSeconds(5), executed)))
                .isEqualTo(LoadSheddingQueue.Admission.ACCEPTED);
        assertThat(queue.enqueue(refresh("old-b", clock, Duration.ofSeconds(5), executed)))
                .isEqualTo(LoadSheddingQueue.Admission.ACCEPTED);
        clock.advance(Duration.ofSeconds(6));
        assertThat(queue.enqueue(new LoadSheddingQueue.Task(
                "payment", LoadSheddingQueue.Kind.CRITICAL, null,
                () -> executed.add("payment"))))
                .isEqualTo(LoadSheddingQueue.Admission.ACCEPTED);

        assertThat(queue.runNext()).isEqualTo(LoadSheddingQueue.Execution.EXECUTED);
        assertThat(queue.runNext()).isEqualTo(LoadSheddingQueue.Execution.EMPTY);
        assertThat(executed).containsExactly("payment");
        assertThat(queue.metrics().expired()).isEqualTo(2);
        assertThat(queue.metrics().executed()).isEqualTo(1);
    }

    @Test
    void criticalWorkMayEvictReplaceableRefreshButNeverAnotherCriticalTask() {
        MutableClock clock = new MutableClock();
        LoadSheddingQueue queue = new LoadSheddingQueue(2, clock);
        List<String> executed = new ArrayList<>();

        queue.enqueue(refresh("metrics", clock, Duration.ofMinutes(1), executed));
        queue.enqueue(new LoadSheddingQueue.Task(
                "order-a", LoadSheddingQueue.Kind.CRITICAL, null,
                () -> executed.add("order-a")));
        assertThat(queue.enqueue(new LoadSheddingQueue.Task(
                "order-b", LoadSheddingQueue.Kind.CRITICAL, null,
                () -> executed.add("order-b"))))
                .isEqualTo(LoadSheddingQueue.Admission.ACCEPTED);

        assertThat(queue.metrics().shedForCapacity()).isEqualTo(1);
        assertThat(queue.metrics().queuedRefresh()).isZero();

        assertThat(queue.enqueue(new LoadSheddingQueue.Task(
                "order-c", LoadSheddingQueue.Kind.CRITICAL, null,
                () -> executed.add("order-c"))))
                .isEqualTo(LoadSheddingQueue.Admission.REJECTED_CRITICAL);

        assertThat(queue.metrics().rejectedCritical()).isEqualTo(1);
        assertThat(queue.runNext()).isEqualTo(LoadSheddingQueue.Execution.EXECUTED);
        assertThat(queue.runNext()).isEqualTo(LoadSheddingQueue.Execution.EXECUTED);
        assertThat(executed).containsExactly("order-a", "order-b");
    }

    @Test
    void expiredAtEnqueueAndCapacityRejectionsAreObservable() {
        MutableClock clock = new MutableClock();
        LoadSheddingQueue queue = new LoadSheddingQueue(1, clock);
        List<String> executed = new ArrayList<>();

        assertThat(queue.enqueue(refresh("old", clock, Duration.ZERO, executed)))
                .isEqualTo(LoadSheddingQueue.Admission.SHED_EXPIRED);
        assertThat(queue.enqueue(refresh("fresh", clock, Duration.ofMinutes(1), executed)))
                .isEqualTo(LoadSheddingQueue.Admission.ACCEPTED);
        assertThat(queue.enqueue(refresh("another", clock, Duration.ofMinutes(1), executed)))
                .isEqualTo(LoadSheddingQueue.Admission.SHED_OVERLOAD);
        assertThat(queue.metrics().expired()).isEqualTo(1);
        assertThat(queue.metrics().shedForCapacity()).isEqualTo(1);
    }

    private LoadSheddingQueue.Task refresh(
            String id, Clock clock, Duration ttl, List<String> executed
    ) {
        return new LoadSheddingQueue.Task(id,
                LoadSheddingQueue.Kind.REPLACEABLE_REFRESH,
                clock.instant().plus(ttl),
                () -> executed.add(id));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-10-27T12:00:00Z");

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
