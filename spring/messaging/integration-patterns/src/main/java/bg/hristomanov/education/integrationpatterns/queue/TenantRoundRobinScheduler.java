package bg.hristomanov.education.integrationpatterns.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Educational per-tenant round robin: one job from each non-empty tenant per
 * scheduling turn, FIFO within each tenant. No fairness by processing cost,
 * SQS-specific heuristics, network, concurrency or durable delivery.
 */
public final class TenantRoundRobinScheduler implements QueueScheduler {
    private final Map<String, Deque<QueuedJob>> byTenant = new HashMap<>();
    private final Deque<String> activeTenants = new ArrayDeque<>();
    private int pendingCount;

    @Override
    public void enqueue(QueuedJob job) {
        Objects.requireNonNull(job, "job");
        Deque<QueuedJob> tenantQueue = byTenant.get(job.tenantId());
        if (tenantQueue == null) {
            tenantQueue = new ArrayDeque<>();
            byTenant.put(job.tenantId(), tenantQueue);
            activeTenants.addLast(job.tenantId());
        }
        tenantQueue.addLast(job);
        pendingCount++;
    }

    @Override
    public Optional<QueuedJob> poll() {
        String tenantId = activeTenants.pollFirst();
        if (tenantId == null) {
            return Optional.empty();
        }

        Deque<QueuedJob> tenantQueue = byTenant.get(tenantId);
        QueuedJob next = tenantQueue.removeFirst();
        if (tenantQueue.isEmpty()) {
            byTenant.remove(tenantId);
        } else {
            activeTenants.addLast(tenantId);
        }
        pendingCount--;
        return Optional.of(next);
    }

    @Override
    public int size() {
        return pendingCount;
    }
}
