package bg.hristomanov.education.systemdesign;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A local, non-blocking concurrency guard (bulkhead / load-shedding demonstration).
 * It is NOT a token-bucket rate limiter, a global multi-node quota, or a durable queue.
 */
public final class BoundedAdmissionGate {
    private final Semaphore permits;
    private final int maxInFlight;
    private final AtomicLong rejected = new AtomicLong();

    public BoundedAdmissionGate(int maxInFlight) {
        if (maxInFlight <= 0) throw new IllegalArgumentException("maxInFlight must be positive");
        this.maxInFlight = maxInFlight;
        this.permits = new Semaphore(maxInFlight);
    }

    public sealed interface Result<T> permits Accepted, Rejected {}

    public record Accepted<T>(T value) implements Result<T> {}
    public record Rejected<T>(String reason) implements Result<T> {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    public <T> Result<T> execute(Supplier<? extends T> work) {
        Objects.requireNonNull(work, "work");
        if (!permits.tryAcquire()) {
            rejected.incrementAndGet();
            return new Rejected<>("AT_CAPACITY");
        }
        try {
            return new Accepted<>(work.get());
        } finally {
            permits.release(); // including when the service code throws
        }
    }

    public int currentlyInFlight() {
        return maxInFlight - permits.availablePermits();
    }

    public long rejectedCount() {
        return rejected.get();
    }
}
