package bg.hristomanov.education.dop.amber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stable Java 25 demonstration of all-components reconstruction.
 *
 * <p>The proposed Amber reconstruction syntax is NOT used. Both changed
 * components must reach one canonical constructor, where invariants are checked.
 */
public final class RecordReconstructionInvariantLab {

    private RecordReconstructionInvariantLab() {
    }

    /**
     * Intentionally awkward parallel-lists model. Preserves the cardinality
     * invariant, even though Java's type system does not enforce it.
     */
    public record ParallelTaskBatch(List<String> taskIds, List<Integer> priorities) {

        public ParallelTaskBatch {
            taskIds = List.copyOf(Objects.requireNonNull(taskIds, "taskIds"));
            priorities = List.copyOf(Objects.requireNonNull(priorities, "priorities"));
            if (taskIds.size() != priorities.size()) {
                throw new IllegalArgumentException("Every task must have exactly one priority");
            }
            Set<String> unique = new HashSet<>();
            for (String taskId : taskIds) {
                if (taskId.isBlank() || !unique.add(taskId)) {
                    throw new IllegalArgumentException("Task IDs must be nonblank and unique");
                }
            }
            for (Integer priority : priorities) {
                if (priority < 1 || priority > 10) {
                    throw new IllegalArgumentException("Priority must be between 1 and 10");
                }
            }
        }

        public ParallelTaskBatch replaceTogether(List<String> newTaskIds,
                                                 List<Integer> newPriorities) {
            // One new object, one validation of the final state.
            return new ParallelTaskBatch(newTaskIds, newPriorities);
        }

        /**
         * Deliberate BAD contrast: this is not an atomic two-component update.
         * Validates intermediate state and fails when cardinality changes.
         */
        public ParallelTaskBatch replaceOnlyTaskIdsForContrast(List<String> newTaskIds) {
            return new ParallelTaskBatch(newTaskIds, priorities);
        }
    }

    /** Better data model: impossible to have an ID without its priority. */
    public record TaskPriority(String taskId, int priority) {

        public TaskPriority {
            Objects.requireNonNull(taskId, "taskId");
            if (taskId.isBlank() || priority < 1 || priority > 10) {
                throw new IllegalArgumentException("Invalid task-priority pair");
            }
        }
    }

    public record ItemizedTaskBatch(List<TaskPriority> tasks) {

        public ItemizedTaskBatch {
            tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
            Set<String> unique = new HashSet<>();
            for (TaskPriority task : tasks) {
                if (!unique.add(task.taskId())) {
                    throw new IllegalArgumentException("Duplicate task ID");
                }
            }
        }

        public ItemizedTaskBatch changePriority(String taskId, int newPriority) {
            Objects.requireNonNull(taskId, "taskId");
            if (tasks.stream().noneMatch(task -> task.taskId().equals(taskId))) {
                throw new IllegalArgumentException("Unknown task: " + taskId);
            }
            List<TaskPriority> updated = new ArrayList<>(tasks.size());
            for (TaskPriority task : tasks) {
                updated.add(task.taskId().equals(taskId)
                        ? new TaskPriority(taskId, newPriority)
                        : task);
            }
            return new ItemizedTaskBatch(updated);
        }
    }

    public static ItemizedTaskBatch normalize(ParallelTaskBatch parallel) {
        Objects.requireNonNull(parallel, "parallel");
        List<TaskPriority> items = new ArrayList<>(parallel.taskIds().size());
        for (int index = 0; index < parallel.taskIds().size(); index++) {
            items.add(new TaskPriority(parallel.taskIds().get(index),
                    parallel.priorities().get(index)));
        }
        return new ItemizedTaskBatch(items);
    }
}
