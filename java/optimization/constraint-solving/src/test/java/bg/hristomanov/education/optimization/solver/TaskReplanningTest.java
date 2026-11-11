package bg.hristomanov.education.optimization.solver;

import bg.hristomanov.education.optimization.model.AssignmentOption;
import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskReplanningTest {

    @Test
    @Timeout(20)
    void pinnedAssignmentSurvivesReplanningAndOldSnapshotIsNotMutated() {
        TaskSchedule old = ScheduleDemoData.create();
        WorkTask initial = old.getTasks().getFirst();
        AssignmentOption committed = old.getOptions().stream()
                .filter(o -> o.id().equals("ANA-MORNING")).findFirst().orElseThrow();
        initial.setAssignment(committed);

        WorkTask newTask = new WorkTask("ADDED", "INSPECTION",
                Set.of("MORNING", "AFTERNOON"), 4, 4);
        TaskSchedule snapshot = TaskReplanning.newSnapshot(
                old, old.getOptions(), List.of(newTask), Set.of(initial.getId()));
        assertNotSame(old, snapshot);
        assertNotSame(old.getTasks().getFirst(), snapshot.getTasks().getFirst());
        assertFalse(old.getTasks().getFirst().isPinned());
        assertTrue(snapshot.getTasks().getFirst().isPinned());

        TaskSchedule solved = new TaskScheduleSolver(Duration.ofMillis(500)).solve(snapshot);
        assertEquals(committed, solved.getTasks().getFirst().getAssignment());
        assertTrue(TaskScheduleSolver.isFeasible(solved));
        assertEquals(IndependentScoreOracle.calculate(solved), solved.getScore());
        assertEquals(committed, old.getTasks().getFirst().getAssignment());
    }

    @Test
    void unassignedExistingTasksRemainUnassignedWhenTakingNewSnapshot() {
        TaskSchedule old = ScheduleDemoData.create();
        assertTrue(old.getTasks().stream().allMatch(task -> task.getAssignment() == null));

        TaskSchedule snapshot = TaskReplanning.newSnapshot(
                old, old.getOptions(), List.of(), Set.of());

        assertEquals(old.getTasks().size(), snapshot.getTasks().size());
        assertTrue(snapshot.getTasks().stream()
                .allMatch(task -> task.getAssignment() == null && !task.isPinned()));
        assertNotSame(old.getTasks().getFirst(), snapshot.getTasks().getFirst());
    }

    @Test
    void removingAnOptionRequiredByAPinnedAssignmentFailsClosed() {
        TaskSchedule old = ScheduleDemoData.create();
        AssignmentOption committed = old.getOptions().getFirst();
        old.getTasks().getFirst().setAssignment(committed);
        old.getTasks().getFirst().setPinned(true);

        List<AssignmentOption> remaining = old.getOptions().stream()
                .filter(o -> !o.equals(committed)).toList();

        assertThrows(IllegalArgumentException.class,
                () -> TaskReplanning.newSnapshot(old, remaining, List.of(), Set.of()));
    }

    @Test
    void duplicateNewTasksOrUnknownCommitIdsFailBeforeSolving() {
        TaskSchedule old = ScheduleDemoData.create();
        WorkTask duplicate = new WorkTask(old.getTasks().getFirst().getId(), "INSPECTION",
                Set.of("MORNING"), 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> TaskReplanning.newSnapshot(old, old.getOptions(),
                        List.of(duplicate), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> TaskReplanning.newSnapshot(old, old.getOptions(),
                        List.of(), Set.of("UNKNOWN")));
    }
}
