package bg.hristomanov.education.optimization.solver;

import bg.hristomanov.education.optimization.model.AssignmentOption;
import bg.hristomanov.education.optimization.model.Employee;
import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.TimeSlot;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskScheduleSolverTest {

    @Test
    @Timeout(20)
    void smallFeasibleCaseCanAssignEverythingWithoutHardViolations() {
        TaskSchedule solved = new TaskScheduleSolver(Duration.ofMillis(500))
                .solve(ScheduleDemoData.create());
        assertNotNull(solved.getScore());
        assertTrue(TaskScheduleSolver.isFeasible(solved));
        assertEquals(0, solved.getScore().mediumScore());
        assertEquals(3, solved.getTasks().stream()
                .filter(t -> t.getAssignment() != null).count());
        assertEquals(IndependentScoreOracle.calculate(solved), solved.getScore());
    }

    @Test
    @Timeout(20)
    void overconstrainedInputPrefersAnUnassignedTaskOverAHardCollision() {
        TimeSlot am = new TimeSlot("AM", 540, 600);
        Employee employee = new Employee("A", Set.of("INSPECTION"),
                Set.of("AM"), Set.of("AM"), 1);
        AssignmentOption option = new AssignmentOption("A-AM", employee, am);
        WorkTask committed = new WorkTask("COMMITTED", "INSPECTION", Set.of("AM"), 10, 1);
        committed.setAssignment(option);
        committed.setPinned(true);
        WorkTask incoming = new WorkTask("NEW", "INSPECTION", Set.of("AM"), 4, 1);
        TaskSchedule solution = new TaskScheduleSolver(Duration.ofMillis(500))
                .solve(new TaskSchedule(List.of(option), List.of(committed, incoming)));

        assertTrue(TaskScheduleSolver.isFeasible(solution));
        WorkTask pinned = solution.getTasks().stream()
                .filter(t -> t.getId().equals("COMMITTED")).findFirst().orElseThrow();
        WorkTask other = solution.getTasks().stream()
                .filter(t -> t.getId().equals("NEW")).findFirst().orElseThrow();
        assertEquals(option, pinned.getAssignment());
        assertEquals(null, other.getAssignment());
        assertTrue(solution.getScore().mediumScore() < 0);
        assertEquals(IndependentScoreOracle.calculate(solution), solution.getScore());
    }

    @Test
    void aHardInfeasibleManualPlanIsNeverReportedAsPublishable() {
        TaskSchedule schedule = ScheduleDemoData.create();
        AssignmentOption option = schedule.getOptions().getFirst();
        schedule.getTasks().get(0).setAssignment(option);
        schedule.getTasks().get(1).setAssignment(option);
        schedule.setScore(IndependentScoreOracle.calculate(schedule));
        assertFalse(TaskScheduleSolver.isFeasible(schedule));
    }

    @Test
    void invalidTerminationBudgetIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TaskScheduleSolver(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskScheduleSolver(Duration.ofSeconds(-1)));
    }
}
