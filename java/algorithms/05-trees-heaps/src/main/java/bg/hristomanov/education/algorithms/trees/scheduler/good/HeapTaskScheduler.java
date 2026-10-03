package bg.hristomanov.education.algorithms.trees.scheduler.good;

import bg.hristomanov.education.algorithms.trees.heap.BinaryHeap;
import bg.hristomanov.education.algorithms.trees.scheduler.ScheduledTask;

import java.util.Comparator;

/**
 * Priority Queue върху binary heap.
 *
 * <p>add и poll са O(log n), а peek е O(1). Това е natural fit за scheduler,
 * който многократно иска "следващата най-приоритетна задача".</p>
 */
public final class HeapTaskScheduler {

    private static final Comparator<ScheduledTask> ORDER =
            Comparator.comparingInt(ScheduledTask::priority)
                    .thenComparingLong(ScheduledTask::sequence);

    private final BinaryHeap<ScheduledTask> heap = new BinaryHeap<>(ORDER);
    private long nextSequence;

    public void schedule(String id, int priority) {
        heap.add(new ScheduledTask(id, priority, nextSequence++));
    }

    public ScheduledTask peekNext() {
        return heap.peek();
    }

    public ScheduledTask pollNext() {
        return heap.poll();
    }

    public long priorityComparisons() {
        return heap.metrics().comparisons();
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }
}
