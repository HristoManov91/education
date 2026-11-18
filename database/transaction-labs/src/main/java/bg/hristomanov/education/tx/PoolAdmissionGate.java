package bg.hristomanov.education.tx;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explicit application-level concurrency budget BEFORE JDBC checkout.
 *
 * This is an illustrative in-process admission gate, not a global database
 * connection limit across all application instances and unrelated callers.
 */
public final class PoolAdmissionGate {
    private final Semaphore permits;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();

    public PoolAdmissionGate(int maxInFlight) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be positive");
        }
        this.permits = new Semaphore(maxInFlight, true);
    }

    public <T> T execute(CheckedSupplier<T> operation) throws Exception {
        Objects.requireNonNull(operation);
        if (!permits.tryAcquire()) {
            rejected.incrementAndGet();
            throw new RejectedExecutionException("DB work admission budget exhausted");
        }
        int current = inFlight.incrementAndGet();
        peakInFlight.accumulateAndGet(current, Math::max);
        try {
            return operation.get();
        } finally {
            inFlight.decrementAndGet();
            permits.release();
        }
    }

    public int inFlight() {
        return inFlight.get();
    }

    public int peakInFlight() {
        return peakInFlight.get();
    }

    public int rejected() {
        return rejected.get();
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
