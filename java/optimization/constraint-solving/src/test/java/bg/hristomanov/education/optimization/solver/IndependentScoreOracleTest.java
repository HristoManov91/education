package bg.hristomanov.education.optimization.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
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

class IndependentScoreOracleTest {

    private final ConstraintVerifier<TaskConstraintProvider, TaskSchedule> verifier =
            ConstraintVerifier.build(new TaskConstraintProvider(),
                    TaskSchedule.class, WorkTask.class);

    @Test
    void exhaustiveNineStatesHaveIdenticalIndependentAndIncrementalScores() {
        TimeSlot am = new TimeSlot("AM", 540, 600);
        TimeSlot pm = new TimeSlot("PM", 840, 900);
        Employee a = new Employee("A", Set.of("INSPECTION"), Set.of("AM"),
                Set.of("AM"), 1);
        Employee b = new Employee("B", Set.of("MAINTENANCE"), Set.of("PM"),
                Set.of("PM"), 4);
        AssignmentOption aAm = new AssignmentOption("A-AM", a, am);
        AssignmentOption bPm = new AssignmentOption("B-PM", b, pm);
        List<AssignmentOption> candidates = List.of(aAm, bPm);

        int checkedStates = 0;
        for (int first = -1; first < candidates.size(); first++) {
            for (int second = -1; second < candidates.size(); second++) {
                WorkTask x = new WorkTask("X", "INSPECTION", Set.of("AM"), 3, 2);
                WorkTask y = new WorkTask("Y", "MAINTENANCE", Set.of("PM"), 5, 5);
                x.setAssignment(first < 0 ? null : candidates.get(first));
                y.setAssignment(second < 0 ? null : candidates.get(second));
                TaskSchedule fixture = new TaskSchedule(candidates, List.of(x, y));

                HardMediumSoftScore expected = IndependentScoreOracle.calculate(fixture);
                verifier.verifyThat().givenSolution(fixture).scores(expected);
                checkedStates++;
            }
        }
        assertEquals(9, checkedStates);
    }

    @Test
    void exhaustiveSmallProblemShowsOneFeasibleFullAssignment() {
        TimeSlot am = new TimeSlot("AM", 540, 600);
        Employee a = new Employee("A", Set.of("INSPECTION"), Set.of("AM"),
                Set.of("AM"), 1);
        AssignmentOption aAm = new AssignmentOption("A-AM", a, am);
        WorkTask first = new WorkTask("X", "INSPECTION", Set.of("AM"), 5, 1);
        WorkTask second = new WorkTask("Y", "INSPECTION", Set.of("AM"), 3, 2);

        HardMediumSoftScore best = null;
        for (int firstAssigned = 0; firstAssigned <= 1; firstAssigned++) {
            for (int secondAssigned = 0; secondAssigned <= 1; secondAssigned++) {
                first.setAssignment(firstAssigned == 1 ? aAm : null);
                second.setAssignment(secondAssigned == 1 ? aAm : null);
                HardMediumSoftScore score = IndependentScoreOracle.calculate(
                        new TaskSchedule(List.of(aAm), List.of(first, second)));
                if (best == null || score.compareTo(best) > 0) {
                    best = score;
                }
            }
        }
        // Only one task may be assigned, the lower-priority one is omitted.
        assertEquals(HardMediumSoftScore.of(0, -3, 0), best);
        assertTrue(best.getHardScore() == 0 && best.getMediumScore() < 0);
    }
}
