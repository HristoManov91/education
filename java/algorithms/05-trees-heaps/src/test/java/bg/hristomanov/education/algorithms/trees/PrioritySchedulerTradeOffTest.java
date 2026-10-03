package bg.hristomanov.education.algorithms.trees;

import bg.hristomanov.education.algorithms.trees.scheduler.ScheduledTask;
import bg.hristomanov.education.algorithms.trees.scheduler.bad.LinearScanTaskScheduler;
import bg.hristomanov.education.algorithms.trees.scheduler.good.HeapTaskScheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrioritySchedulerTradeOffTest {

    @Test
    void heapSchedulerPreservesPrioritySemanticsWithFarFewerComparisons() {
        int taskCount = 1_000;
        LinearScanTaskScheduler badScheduler = new LinearScanTaskScheduler();
        HeapTaskScheduler goodScheduler = new HeapTaskScheduler();

        for (int priority = taskCount; priority >= 1; priority--) {
            String id = "task-" + priority;
            badScheduler.schedule(id, priority);
            goodScheduler.schedule(id, priority);
        }

        List<String> badOrder = drain(badScheduler);
        List<String> goodOrder = drain(goodScheduler);

        assertThat(goodOrder).isEqualTo(badOrder);
        assertThat(badScheduler.priorityComparisons()).isEqualTo(499_500);
        assertThat(goodScheduler.priorityComparisons()).isLessThan(30_000);
        assertThat(badScheduler.priorityComparisons())
                .isGreaterThan(goodScheduler.priorityComparisons() * 10);
    }

    @Test
    void samePriorityTasksKeepSubmissionOrder() {
        LinearScanTaskScheduler badScheduler = new LinearScanTaskScheduler();
        HeapTaskScheduler goodScheduler = new HeapTaskScheduler();

        badScheduler.schedule("A", 5);
        badScheduler.schedule("B", 5);
        badScheduler.schedule("C", 5);

        goodScheduler.schedule("A", 5);
        goodScheduler.schedule("B", 5);
        goodScheduler.schedule("C", 5);

        assertThat(drain(goodScheduler)).containsExactly("A", "B", "C");
        assertThat(drain(badScheduler)).containsExactly("A", "B", "C");
    }

    private List<String> drain(LinearScanTaskScheduler scheduler) {
        List<String> order = new ArrayList<>();
        while (!scheduler.isEmpty()) {
            ScheduledTask task = scheduler.pollNext();
            order.add(task.id());
        }
        return List.copyOf(order);
    }

    private List<String> drain(HeapTaskScheduler scheduler) {
        List<String> order = new ArrayList<>();
        while (!scheduler.isEmpty()) {
            ScheduledTask task = scheduler.pollNext();
            order.add(task.id());
        }
        return List.copyOf(order);
    }
}
