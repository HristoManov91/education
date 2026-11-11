package bg.hristomanov.education.optimization.solver;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import bg.hristomanov.education.optimization.model.AssignmentOption;
import bg.hristomanov.education.optimization.model.Employee;
import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.TimeSlot;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskConstraintProviderTest {

    private final ConstraintVerifier<TaskConstraintProvider, TaskSchedule> verifier =
            ConstraintVerifier.build(new TaskConstraintProvider(),
                    TaskSchedule.class, WorkTask.class);

    private static final TimeSlot MORNING = new TimeSlot("AM", 540, 600);
    private static final TimeSlot AFTERNOON = new TimeSlot("PM", 840, 900);

    private static Employee worker(String id, String skill, Set<String> available,
                                   Set<String> preferred, int zone) {
        return new Employee(id, Set.of(skill), available, preferred, zone);
    }

    private static WorkTask task(String id, String skill, AssignmentOption option,
                                 Set<String> allowedSlots, int priority, int zone) {
        WorkTask result = new WorkTask(id, skill, allowedSlots, priority, zone);
        result.setAssignment(option);
        return result;
    }

    @Test
    void skillMismatchCostsExactlyOneHardPenalty() {
        Employee worker = worker("A", "INSPECTION", Set.of("AM"), Set.of("AM"), 1);
        AssignmentOption option = new AssignmentOption("A-AM", worker, MORNING);
        WorkTask noSkill = task("T1", "MAINTENANCE", option, Set.of("AM"), 4, 1);
        verifier.verifyThat(TaskConstraintProvider::skillRequirement)
                .given(noSkill).penalizesBy(1);
    }

    @Test
    void unavailableEmployeeCostsOneHardPenalty() {
        Employee worker = worker("A", "INSPECTION", Set.of("PM"), Set.of("AM"), 1);
        WorkTask task = task("T1", "INSPECTION",
                new AssignmentOption("A-AM", worker, MORNING), Set.of("AM"), 4, 1);
        verifier.verifyThat(TaskConstraintProvider::employeeAvailability)
                .given(task).penalizesBy(1);
    }

    @Test
    void taskOutsideItsTimeWindowCostsOneHardPenalty() {
        Employee worker = worker("A", "INSPECTION", Set.of("AM"), Set.of("AM"), 1);
        WorkTask task = task("T1", "INSPECTION",
                new AssignmentOption("A-AM", worker, MORNING), Set.of("PM"), 4, 1);
        verifier.verifyThat(TaskConstraintProvider::taskTimeWindow)
                .given(task).penalizesBy(1);
    }

    @Test
    void sameEmployeeAndSlotConflictsRegardlessOfTaskSkill() {
        Employee worker = worker("A", "INSPECTION", Set.of("AM", "PM"), Set.of("AM"), 1);
        WorkTask first = task("T1", "INSPECTION",
                new AssignmentOption("A-AM", worker, MORNING), Set.of("AM"), 4, 1);
        WorkTask second = task("T2", "INSPECTION",
                new AssignmentOption("A-AM", worker, MORNING), Set.of("AM"), 5, 1);
        WorkTask separateSlot = task("T3", "INSPECTION",
                new AssignmentOption("A-PM", worker, AFTERNOON), Set.of("PM"), 6, 1);
        verifier.verifyThat(TaskConstraintProvider::employeeCollision)
                .given(first, second, separateSlot).penalizesBy(1);
    }

    @Test
    void anUnassignedTaskMustNotSilentlyDisappearFromTheScore() {
        WorkTask missing = task("T1", "INSPECTION", null, Set.of("AM"), 9, 1);
        verifier.verifyThat(TaskConstraintProvider::unassignedPriority)
                .given(missing).penalizesBy(9);
    }

    @Test
    void distanceAndPreferencePenaltiesAreIndependentSoftGoals() {
        Employee worker = worker("A", "INSPECTION", Set.of("AM", "PM"), Set.of("AM"), 2);
        WorkTask far = task("T1", "INSPECTION",
                new AssignmentOption("A-PM", worker, AFTERNOON), Set.of("PM"), 4, 7);
        verifier.verifyThat(TaskConstraintProvider::travelDistance)
                .given(far).penalizesBy(5);
        verifier.verifyThat(TaskConstraintProvider::preferredSlot)
                .given(far).penalizesBy(2);
    }

    @Test
    void hardRulesAlwaysOutrankEvenVeryLargeSoftImprovements() {
        HardMediumSoftScore safe = HardMediumSoftScore.of(0, -5, -10_000);
        HardMediumSoftScore invalid = HardMediumSoftScore.of(-1, 0, 0);
        assertTrue(safe.compareTo(invalid) > 0);
        assertTrue(HardMediumSoftScore.of(0, 0, -10_000)
                .compareTo(HardMediumSoftScore.of(0, -1, 0)) > 0);
    }

    @Test
    void entireScoreMatchesIndependentOracleOnKnownNegativeFixture() {
        Employee worker = worker("A", "INSPECTION", Set.of("AM"), Set.of("AM"), 1);
        AssignmentOption option = new AssignmentOption("A-AM", worker, MORNING);
        WorkTask first = task("T1", "INSPECTION", option, Set.of("AM"), 7, 1);
        WorkTask collision = task("T2", "MAINTENANCE", option, Set.of("AM"), 5, 3);
        WorkTask missing = task("T3", "INSPECTION", null, Set.of("PM"), 8, 9);
        TaskSchedule fixture = new TaskSchedule(List.of(option), List.of(first, collision, missing));
        HardMediumSoftScore oracle = IndependentScoreOracle.calculate(fixture);
        assertEquals(HardMediumSoftScore.of(-2, -8, -2), oracle);
        verifier.verifyThat().givenSolution(fixture).scores(oracle);
    }
}
