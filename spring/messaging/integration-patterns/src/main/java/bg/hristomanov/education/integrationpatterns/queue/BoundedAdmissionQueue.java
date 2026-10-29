package bg.hristomanov.education.integrationpatterns.queue;

import java.util.Objects;
import java.util.Optional;

/**
 * Bounded pending-message admission; rejection is explicit and never an ACK.
 * The caller must retain rejected work or apply a documented shedding policy.
 * Does not include in-flight work, storage durability or concurrent producers.
 */
public final class BoundedAdmissionQueue {
    public enum Admission {
        ACCEPTED,
        REJECTED_CAPACITY
    }

    private final QueueScheduler scheduler;
    private final int maxPending;

    public BoundedAdmissionQueue(QueueScheduler scheduler, int maxPending) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (maxPending <= 0) {
            throw new IllegalArgumentException("maxPending must be positive");
        }
        this.maxPending = maxPending;
    }

    public Admission offer(QueuedJob job) {
        Objects.requireNonNull(job, "job");
        if (scheduler.size() >= maxPending) {
            return Admission.REJECTED_CAPACITY;
        }
        scheduler.enqueue(job);
        return Admission.ACCEPTED;
    }

    public Optional<QueuedJob> poll() {
        return scheduler.poll();
    }

    public int size() {
        return scheduler.size();
    }
}
