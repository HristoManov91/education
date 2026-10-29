package bg.hristomanov.education.integrationpatterns.queue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueFairnessSimulationTest {

    @Test
    void oneNoisyTenantDelaysQuietTenantsUnderGlobalFifoButNotUnderRoundRobin() {
        QueueSimulation.Result fifo = runSameBurst(new GlobalFifoScheduler());
        QueueSimulation.Result fair = runSameBurst(new TenantRoundRobinScheduler());

        // Fixed reproducible assumptions: one worker, every job arrives at t=0,
        // each processing duration = one tick, 80 noisy and 5 quiet messages.
        assertThat(fifo.p95DwellTicks("quiet")).isEqualTo(84);
        assertThat(fair.p95DwellTicks("quiet")).isEqualTo(9);
        assertThat(fifo.worstDwellTicks("quiet")).isEqualTo(84);
        assertThat(fair.worstDwellTicks("quiet")).isEqualTo(9);

        // Fairness is not extra capacity: both consume exactly the same work.
        assertThat(fifo.makespanTicks()).isEqualTo(85);
        assertThat(fair.makespanTicks()).isEqualTo(85);
        assertThat(fifo.processedCount()).isEqualTo(85);
        assertThat(fair.processedCount()).isEqualTo(85);
        assertThat(fair.dwellTimesByTenant().get("noisy")).hasSize(80);
        assertThat(fair.dwellTimesByTenant().get("quiet")).hasSize(5);
    }

    @Test
    void roundRobinPreservesOrderWithinTenantAndAlternatesActiveTenants() {
        QueueScheduler scheduler = new TenantRoundRobinScheduler();
        scheduler.enqueue(new QueuedJob("noisy", "A1", 1));
        scheduler.enqueue(new QueuedJob("noisy", "A2", 1));
        scheduler.enqueue(new QueuedJob("quiet", "B1", 1));
        scheduler.enqueue(new QueuedJob("quiet", "B2", 1));

        List<String> ids = List.of(
                scheduler.poll().orElseThrow().jobId(),
                scheduler.poll().orElseThrow().jobId(),
                scheduler.poll().orElseThrow().jobId(),
                scheduler.poll().orElseThrow().jobId());

        assertThat(ids).containsExactly("A1", "B1", "A2", "B2");
        assertThat(scheduler.size()).isZero();
        assertThat(scheduler.poll()).isEmpty();
    }

    @Test
    void roundRobinByJobCountDoesNotPreemptExpensiveJobs() {
        QueueScheduler scheduler = new TenantRoundRobinScheduler();
        scheduler.enqueue(new QueuedJob("noisy", "long-running", 50));
        scheduler.enqueue(new QueuedJob("quiet", "one-tick", 1));

        QueueSimulation.Result result = QueueSimulation.drain(scheduler);

        // One non-preemptive heavy job still delays the quiet tenant.
        // Per-job scheduling is NOT processing-time-weighted fairness.
        assertThat(result.p95DwellTicks("quiet")).isEqualTo(50);
        assertThat(result.makespanTicks()).isEqualTo(51);
        assertThat(result.processedCount()).isEqualTo(2);
    }

    @Test
    void aFullQueueExplicitlyRejectsNewJobsInsteadOfPretendingSuccess() {
        BoundedAdmissionQueue queue = new BoundedAdmissionQueue(
                new TenantRoundRobinScheduler(), 2);

        assertThat(queue.offer(new QueuedJob("A", "a1", 1)))
                .isEqualTo(BoundedAdmissionQueue.Admission.ACCEPTED);
        assertThat(queue.offer(new QueuedJob("B", "b1", 1)))
                .isEqualTo(BoundedAdmissionQueue.Admission.ACCEPTED);
        assertThat(queue.offer(new QueuedJob("C", "c1", 1)))
                .isEqualTo(BoundedAdmissionQueue.Admission.REJECTED_CAPACITY);

        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.poll().orElseThrow().jobId()).isEqualTo("a1");
        assertThat(queue.poll().orElseThrow().jobId()).isEqualTo("b1");
        assertThat(queue.poll()).isEmpty();
    }

    @Test
    void invalidJobsOrCapacityFailFastAndResultsAreReadOnly() {
        assertThatThrownBy(() -> new QueuedJob(" ", "id", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueuedJob("A", "id", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedAdmissionQueue(new GlobalFifoScheduler(), 0))
                .isInstanceOf(IllegalArgumentException.class);

        QueueScheduler scheduler = new TenantRoundRobinScheduler();
        scheduler.enqueue(new QueuedJob("A", "id", 1));
        QueueSimulation.Result result = QueueSimulation.drain(scheduler);
        assertThatThrownBy(() -> result.dwellTimesByTenant().get("A").add(100L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.p95DwellTicks("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static QueueSimulation.Result runSameBurst(QueueScheduler scheduler) {
        for (int i = 0; i < 80; i++) {
            scheduler.enqueue(new QueuedJob("noisy", "hot-" + i, 1));
        }
        for (int i = 0; i < 5; i++) {
            scheduler.enqueue(new QueuedJob("quiet", "quiet-" + i, 1));
        }
        return QueueSimulation.drain(scheduler);
    }
}
