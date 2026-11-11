package bg.hristomanov.education.optimization.model;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import java.util.List;
import java.util.Objects;

@PlanningSolution
public class TaskSchedule {

    @ValueRangeProvider(id = "options")
    @ProblemFactCollectionProperty
    private List<AssignmentOption> options;
    @PlanningEntityCollectionProperty
    private List<WorkTask> tasks;
    @PlanningScore
    private HardMediumSoftScore score;

    public TaskSchedule() {
        // Required for planning solution cloning/construction.
    }

    public TaskSchedule(List<AssignmentOption> options, List<WorkTask> tasks) {
        this.options = List.copyOf(Objects.requireNonNull(options, "options"));
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
    }

    public List<AssignmentOption> getOptions() {
        return options;
    }

    public List<WorkTask> getTasks() {
        return tasks;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
