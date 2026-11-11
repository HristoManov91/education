package bg.hristomanov.education.optimization.solver;

import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.time.Duration;

/** Main method for a reproducible CLI smoke check on Java 25. */
public final class ScheduleDemoMain {

    private ScheduleDemoMain() {
    }

    public static void main(String[] args) {
        TaskSchedule solved = new TaskScheduleSolver(Duration.ofSeconds(1))
                .solve(ScheduleDemoData.create());
        System.out.println("SCORE=" + solved.getScore());
        System.out.println("FEASIBLE=" + TaskScheduleSolver.isFeasible(solved));
        for (WorkTask task : solved.getTasks()) {
            System.out.println(task.getId() + "="
                    + (task.getAssignment() == null ? "UNASSIGNED" : task.getAssignment().id()));
        }
        System.out.println("ORACLE_MATCHES="
                + solved.getScore().equals(IndependentScoreOracle.calculate(solved)));
    }
}
