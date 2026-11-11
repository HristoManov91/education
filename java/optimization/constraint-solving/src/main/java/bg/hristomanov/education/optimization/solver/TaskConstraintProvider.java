package bg.hristomanov.education.optimization.solver;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import bg.hristomanov.education.optimization.model.WorkTask;

/**
 * Every constraint is incremental and in-memory. Do not query a DB or an HTTP
 * service from here: a solver may evaluate moves many thousands of times.
 */
public final class TaskConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                skillRequirement(factory), employeeAvailability(factory),
                taskTimeWindow(factory), employeeCollision(factory),
                unassignedPriority(factory), travelDistance(factory),
                preferredSlot(factory)
        };
    }

    // HARD: business rules are never exchanged for medium/soft improvements.
    public Constraint skillRequirement(ConstraintFactory factory) {
        return factory.forEach(WorkTask.class)
                .filter(task -> !task.getAssignment().employee().skills()
                        .contains(task.getRequiredSkill()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Required skill");
    }

    public Constraint employeeAvailability(ConstraintFactory factory) {
        return factory.forEach(WorkTask.class)
                .filter(task -> !task.getAssignment().employee().availableSlots()
                        .contains(task.getAssignment().slot().id()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Employee availability");
    }

    public Constraint taskTimeWindow(ConstraintFactory factory) {
        return factory.forEach(WorkTask.class)
                .filter(task -> !task.getAllowedSlotIds().contains(task.getAssignment().slot().id()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Task time window");
    }

    public Constraint employeeCollision(ConstraintFactory factory) {
        return factory.forEachUniquePair(WorkTask.class,
                        Joiners.equal(t -> t.getAssignment().employee().id()),
                        Joiners.equal(t -> t.getAssignment().slot().id()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("One employee per time slot");
    }

    // MEDIUM: tasks left unassigned must not vanish without a penalty.
    public Constraint unassignedPriority(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(WorkTask.class)
                .filter(task -> task.getAssignment() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM, WorkTask::getPriority)
                .asConstraint("Unassigned task priority");
    }

    // SOFT: minimize the distance proxy and respect preferred working hours.
    public Constraint travelDistance(ConstraintFactory factory) {
        return factory.forEach(WorkTask.class)
                .penalize(HardMediumSoftScore.ONE_SOFT, task ->
                        Math.abs(task.getZone() - task.getAssignment().employee().homeZone()))
                .asConstraint("Travel distance proxy");
    }

    public Constraint preferredSlot(ConstraintFactory factory) {
        return factory.forEach(WorkTask.class)
                .filter(task -> !task.getAssignment().employee().preferredSlots()
                        .contains(task.getAssignment().slot().id()))
                .penalize(HardMediumSoftScore.ONE_SOFT, task -> 2)
                .asConstraint("Employee slot preference");
    }
}
