package bg.hristomanov.education.integrationpatterns.queue;

import java.util.Optional;

/**
 * In-memory scheduling policy only, NOT a durable broker or thread-safe queue.
 * Admission control and acknowledgement/retries are separate concerns.
 */
public interface QueueScheduler {
    void enqueue(QueuedJob job);

    Optional<QueuedJob> poll();

    int size();
}
