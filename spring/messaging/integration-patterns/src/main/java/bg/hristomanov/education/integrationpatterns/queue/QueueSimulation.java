package bg.hristomanov.education.integrationpatterns.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Discrete, deterministic single-worker simulation. All submitted jobs are
 * present at tick zero. One job runs to completion before the next one starts.
 * The recorded dwell tick is queue wait before processing, not response time.
 */
public final class QueueSimulation {
    private QueueSimulation() {
    }

    public static Result drain(QueueScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        Map<String, List<Long>> dwellTimes = new LinkedHashMap<>();
        long currentTick = 0;
        int processed = 0;

        while (true) {
            var next = scheduler.poll();
            if (next.isEmpty()) {
                break;
            }
            QueuedJob job = next.orElseThrow();
            dwellTimes.computeIfAbsent(job.tenantId(), ignored -> new ArrayList<>())
                    .add(currentTick);
            currentTick = Math.addExact(currentTick, job.processingTicks());
            processed++;
        }
        return new Result(currentTick, processed, dwellTimes);
    }

    public record Result(long makespanTicks, int processedCount,
                         Map<String, List<Long>> dwellTimesByTenant) {
        public Result {
            if (makespanTicks < 0 || processedCount < 0) {
                throw new IllegalArgumentException("invalid simulation result");
            }
            Objects.requireNonNull(dwellTimesByTenant, "dwellTimesByTenant");
            Map<String, List<Long>> copy = new LinkedHashMap<>();
            dwellTimesByTenant.forEach((key, values) ->
                    copy.put(key, List.copyOf(values)));
            dwellTimesByTenant = Collections.unmodifiableMap(copy);
        }

        public long p95DwellTicks(String tenantId) {
            List<Long> waits = dwellTimesByTenant.get(tenantId);
            if (waits == null || waits.isEmpty()) {
                throw new IllegalArgumentException("unknown tenant: " + tenantId);
            }
            List<Long> sorted = new ArrayList<>(waits);
            Collections.sort(sorted);
            // Nearest-rank p95; deliberately not interpolated.
            int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
            return sorted.get(index);
        }

        public long worstDwellTicks(String tenantId) {
            List<Long> waits = dwellTimesByTenant.get(tenantId);
            if (waits == null || waits.isEmpty()) {
                throw new IllegalArgumentException("unknown tenant: " + tenantId);
            }
            return Collections.max(waits);
        }
    }
}
