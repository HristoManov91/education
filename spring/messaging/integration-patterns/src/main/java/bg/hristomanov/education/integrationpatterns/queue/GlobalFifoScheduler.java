package bg.hristomanov.education.integrationpatterns.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/** Baseline: earliest enqueued job wins, regardless of its tenant. */
public final class GlobalFifoScheduler implements QueueScheduler {
    private final Deque<QueuedJob> pending = new ArrayDeque<>();

    @Override
    public void enqueue(QueuedJob job) {
        pending.addLast(Objects.requireNonNull(job, "job"));
    }

    @Override
    public Optional<QueuedJob> poll() {
        return Optional.ofNullable(pending.pollFirst());
    }

    @Override
    public int size() {
        return pending.size();
    }
}
