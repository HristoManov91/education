package bg.hristomanov.education.integrationpatterns.queue;

import java.util.Objects;

/**
 * A message awaiting one sequential, non-preemptive worker. All jobs used in
 * the deterministic batch simulation arrive at tick zero.
 */
public record QueuedJob(String tenantId, String jobId, int processingTicks) {

    public QueuedJob {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(jobId, "jobId");
        if (tenantId.isBlank() || jobId.isBlank()) {
            throw new IllegalArgumentException("tenantId and jobId must not be blank");
        }
        if (processingTicks <= 0) {
            throw new IllegalArgumentException("processingTicks must be positive");
        }
    }
}
