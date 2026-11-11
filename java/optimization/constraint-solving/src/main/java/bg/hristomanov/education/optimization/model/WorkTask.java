package bg.hristomanov.education.optimization.model;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable planning entity. Never derive equals()/hashCode() from a planning
 * variable: Timefold changes assignments while solving.
 */
@PlanningEntity
public class WorkTask {

    @PlanningId
    private String id;
    private String requiredSkill;
    private Set<String> allowedSlotIds;
    private int priority;
    private int zone;

    @PlanningVariable(valueRangeProviderRefs = "options", allowsUnassigned = true)
    private AssignmentOption assignment;
    @PlanningPin
    private boolean pinned;

    public WorkTask() {
        // Timefold reflective construction.
    }

    public WorkTask(String id, String requiredSkill, Set<String> allowedSlotIds,
                    int priority, int zone) {
        this.id = Objects.requireNonNull(id, "id");
        this.requiredSkill = Objects.requireNonNull(requiredSkill, "requiredSkill");
        this.allowedSlotIds = Set.copyOf(Objects.requireNonNull(allowedSlotIds, "allowedSlotIds"));
        if (id.isBlank() || requiredSkill.isBlank() || this.allowedSlotIds.isEmpty()
                || priority < 1 || priority > 10) {
            throw new IllegalArgumentException("Invalid task input");
        }
        this.priority = priority;
        this.zone = zone;
    }

    public String getId() {
        return id;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public Set<String> getAllowedSlotIds() {
        return allowedSlotIds;
    }

    public int getPriority() {
        return priority;
    }

    public int getZone() {
        return zone;
    }

    public AssignmentOption getAssignment() {
        return assignment;
    }

    public void setAssignment(AssignmentOption assignment) {
        this.assignment = assignment;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return "Task(" + id + ", assigned=" + (assignment == null ? "none" : assignment.id()) + ")";
    }
}
