package bg.hristomanov.education.optimization.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import bg.hristomanov.education.optimization.model.AssignmentOption;
import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.util.List;

/**
 * Slow, deliberately non-incremental reference score. This must NOT depend on
 * ConstraintProvider or its own helper methods; it acts as an independent oracle
 * for small fixtures and exhaustively enumerated micro-problems.
 */
public final class IndependentScoreOracle {

    private IndependentScoreOracle() {
    }

    public static HardMediumSoftScore calculate(TaskSchedule schedule) {
        int hard = 0;
        int medium = 0;
        int soft = 0;
        List<WorkTask> tasks = schedule.getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            WorkTask task = tasks.get(index);
            AssignmentOption chosen = task.getAssignment();
            if (chosen == null) {
                medium = Math.addExact(medium, task.getPriority());
                continue;
            }
            if (!chosen.employee().skills().contains(task.getRequiredSkill())) {
                hard++;
            }
            if (!chosen.employee().availableSlots().contains(chosen.slot().id())) {
                hard++;
            }
            if (!task.getAllowedSlotIds().contains(chosen.slot().id())) {
                hard++;
            }
            soft = Math.addExact(soft, Math.abs(task.getZone() - chosen.employee().homeZone()));
            if (!chosen.employee().preferredSlots().contains(chosen.slot().id())) {
                soft = Math.addExact(soft, 2);
            }
            // Count each collision once. No pair constraint stream or Joiners here.
            for (int nextIndex = 0; nextIndex < index; nextIndex++) {
                AssignmentOption earlier = tasks.get(nextIndex).getAssignment();
                if (earlier != null
                        && earlier.employee().id().equals(chosen.employee().id())
                        && earlier.slot().id().equals(chosen.slot().id())) {
                    hard++;
                }
            }
        }
        return HardMediumSoftScore.of(-hard, -medium, -soft);
    }
}
