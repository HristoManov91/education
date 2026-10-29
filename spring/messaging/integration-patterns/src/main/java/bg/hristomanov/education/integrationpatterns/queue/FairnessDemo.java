package bg.hristomanov.education.integrationpatterns.queue;

/**
 * Run after "mvn -pl spring/messaging/integration-patterns compile":
 * java -cp spring/messaging/integration-patterns/target/classes
 *      bg.hristomanov.education.integrationpatterns.queue.FairnessDemo
 */
public final class FairnessDemo {
    private FairnessDemo() {
    }

    public static void main(String[] args) {
        QueueSimulation.Result fifo = runSameBurst(new GlobalFifoScheduler());
        QueueSimulation.Result fair = runSameBurst(new TenantRoundRobinScheduler());

        System.out.printf("Global FIFO: quiet p95 dwell = %d ticks; makespan = %d ticks%n",
                fifo.p95DwellTicks("quiet"), fifo.makespanTicks());
        System.out.printf("Tenant round robin: quiet p95 dwell = %d ticks; makespan = %d ticks%n",
                fair.p95DwellTicks("quiet"), fair.makespanTicks());
        System.out.println("This is a single-worker, zero-arrival-time, in-memory simulation.");
    }

    private static QueueSimulation.Result runSameBurst(QueueScheduler scheduler) {
        for (int i = 0; i < 80; i++) {
            scheduler.enqueue(new QueuedJob("noisy", "noisy-" + i, 1));
        }
        for (int i = 0; i < 5; i++) {
            scheduler.enqueue(new QueuedJob("quiet", "quiet-" + i, 1));
        }
        return QueueSimulation.drain(scheduler);
    }
}
