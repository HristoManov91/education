package bg.hristomanov.education.algorithms.trees.scheduler;

/**
 * По-малка priority стойност означава по-висок приоритет.
 * sequence пази FIFO order при еднакъв priority.
 */
public record ScheduledTask(
        String id,
        int priority,
        long sequence
) {
}
