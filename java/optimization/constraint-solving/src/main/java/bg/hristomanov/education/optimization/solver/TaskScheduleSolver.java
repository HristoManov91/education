package bg.hristomanov.education.optimization.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.time.Duration;
import java.util.Objects;

/**
 * Bounded synchronous adapter for CLI and deterministic correctness fixtures.
 * Do NOT execute solve() on a REST request thread in a real service.
 */
public final class TaskScheduleSolver {

    private final SolverFactory<TaskSchedule> factory;

    public TaskScheduleSolver(Duration timeBudget) {
        Objects.requireNonNull(timeBudget, "timeBudget");
        if (timeBudget.isNegative() || timeBudget.isZero()) {
            throw new IllegalArgumentException("Time budget must be positive");
        }
        SolverConfig config = new SolverConfig()
                .withSolutionClass(TaskSchedule.class)
                .withEntityClasses(WorkTask.class)
                .withConstraintProviderClass(TaskConstraintProvider.class)
                .withTerminationSpentLimit(timeBudget);
        factory = SolverFactory.create(config);
    }

    public TaskSchedule solve(TaskSchedule input) {
        Objects.requireNonNull(input, "input");
        TaskSchedule solution = factory.buildSolver().solve(input);
        if (solution.getScore() == null) {
            throw new IllegalStateException("Solver did not produce a score");
        }
        return solution;
    }

    /** Distinguish an initialized plan from a plan respecting all HARD rules. */
    public static boolean isFeasible(TaskSchedule solution) {
        HardMediumSoftScore score = Objects.requireNonNull(solution.getScore(), "score");
        return score.getHardScore() == 0 && score.isSolutionInitialized();
    }

    public SolverFactory<TaskSchedule> getFactory() {
        return factory;
    }
}
