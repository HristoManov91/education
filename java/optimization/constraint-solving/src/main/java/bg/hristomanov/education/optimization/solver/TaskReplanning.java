package bg.hristomanov.education.optimization.solver;

import bg.hristomanov.education.optimization.model.AssignmentOption;
import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * BATCH replanning: build a fresh problem snapshot, pin committed assignments,
 * then call Solver.solve() on the new snapshot. This is not a live ProblemChange.
 *
 * <p>The previously published solution is not changed. A pinned option which
 * disappeared from the input is rejected instead of silently moving it.
 */
public final class TaskReplanning {

    private TaskReplanning() {
    }

    public static TaskSchedule newSnapshot(TaskSchedule previous,
                                           List<AssignmentOption> currentOptions,
                                           List<WorkTask> additionalTasks,
                                           Set<String> committedTaskIds) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(currentOptions, "currentOptions");
        Objects.requireNonNull(additionalTasks, "additionalTasks");
        Objects.requireNonNull(committedTaskIds, "committedTaskIds");

        List<AssignmentOption> options = List.copyOf(currentOptions);
        if (new HashSet<>(options.stream().map(AssignmentOption::id).toList()).size()
                != options.size()) {
            throw new IllegalArgumentException("Duplicate assignment option ids");
        }
        List<WorkTask> copied = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> existingIds = new HashSet<>();
        for (WorkTask original : previous.getTasks()) {
            if (!seen.add(original.getId())) {
                throw new IllegalArgumentException("Duplicate task id");
            }
            existingIds.add(original.getId());
            WorkTask entity = copy(original);
            boolean locked = original.isPinned() || committedTaskIds.contains(original.getId());
            AssignmentOption current = original.getAssignment();
            if (locked) {
                if (current == null || !options.contains(current)) {
                    throw new IllegalArgumentException(
                            "Committed assignment missing from new values: " + original.getId());
                }
                entity.setAssignment(current);
                entity.setPinned(true);
            } else {
                // Immutable List.copyOf().contains(null) throws NullPointerException.
                // Unassigned is a legitimate planning state and must remain null.
                entity.setAssignment(current != null && options.contains(current) ? current : null);
            }
            copied.add(entity);
        }
        if (!existingIds.containsAll(committedTaskIds)) {
            throw new IllegalArgumentException("Cannot pin an unknown task id");
        }
        for (WorkTask task : additionalTasks) {
            if (!seen.add(task.getId())) {
                throw new IllegalArgumentException("Duplicate new task id");
            }
            if (task.isPinned() || task.getAssignment() != null) {
                throw new IllegalArgumentException("New tasks must arrive unassigned and unpinned");
            }
            copied.add(copy(task));
        }
        return new TaskSchedule(options, copied);
    }

    private static WorkTask copy(WorkTask task) {
        return new WorkTask(task.getId(), task.getRequiredSkill(), task.getAllowedSlotIds(),
                task.getPriority(), task.getZone());
    }
}
