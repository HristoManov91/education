package bg.hristomanov.education.algorithms.trees.scheduler.bad;

import bg.hristomanov.education.algorithms.trees.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Наивен Priority Queue: добавянето е евтино, но pollNext() сканира всички задачи.
 *
 * <p>Ако извадим всички n задачи една по една, priority comparisons стават
 * O(n²). ArrayList.remove(index) добавя и shifting cost, който нарочно не броим
 * тук — сравнението вече е достатъчно да покаже problem shape-а.</p>
 */
public final class LinearScanTaskScheduler {

    private static final Comparator<ScheduledTask> ORDER =
            Comparator.comparingInt(ScheduledTask::priority)
                    .thenComparingLong(ScheduledTask::sequence);

    private final List<ScheduledTask> tasks = new ArrayList<>();
    private long nextSequence;
    private long priorityComparisons;

    public void schedule(String id, int priority) {
        tasks.add(new ScheduledTask(id, priority, nextSequence++));
    }

    public ScheduledTask pollNext() {
        if (tasks.isEmpty()) {
            throw new IllegalStateException("scheduler is empty");
        }

        int bestIndex = 0;

        for (int i = 1; i < tasks.size(); i++) {
            priorityComparisons++;
            if (ORDER.compare(tasks.get(i), tasks.get(bestIndex)) < 0) {
                bestIndex = i;
            }
        }

        return tasks.remove(bestIndex);
    }

    public long priorityComparisons() {
        return priorityComparisons;
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}
