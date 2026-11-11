package bg.hristomanov.education.optimization.solver;

import bg.hristomanov.education.optimization.model.AssignmentOption;
import bg.hristomanov.education.optimization.model.Employee;
import bg.hristomanov.education.optimization.model.TaskSchedule;
import bg.hristomanov.education.optimization.model.TimeSlot;
import bg.hristomanov.education.optimization.model.WorkTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Completely synthetic data; no personal data, cloud API or DB dependency. */
public final class ScheduleDemoData {

    private ScheduleDemoData() {
    }

    public static TaskSchedule create() {
        TimeSlot morning = new TimeSlot("MORNING", 9 * 60, 10 * 60);
        TimeSlot afternoon = new TimeSlot("AFTERNOON", 14 * 60, 15 * 60);

        Employee ana = new Employee("ANA", Set.of("INSPECTION", "MAINTENANCE"),
                Set.of("MORNING", "AFTERNOON"), Set.of("MORNING"), 1);
        Employee boris = new Employee("BORIS", Set.of("INSPECTION"),
                Set.of("MORNING", "AFTERNOON"), Set.of("AFTERNOON"), 7);

        List<AssignmentOption> options = new ArrayList<>();
        for (Employee employee : List.of(ana, boris)) {
            for (TimeSlot slot : List.of(morning, afternoon)) {
                options.add(new AssignmentOption(employee.id() + "-" + slot.id(),
                        employee, slot));
            }
        }

        WorkTask gate = new WorkTask("GATE-CHECK", "INSPECTION",
                Set.of("MORNING"), 10, 1);
        WorkTask sensor = new WorkTask("SENSOR-MAINT", "MAINTENANCE",
                Set.of("AFTERNOON"), 8, 2);
        WorkTask ramp = new WorkTask("RAMP-CHECK", "INSPECTION",
                Set.of("MORNING", "AFTERNOON"), 6, 6);
        return new TaskSchedule(options, List.of(gate, sensor, ramp));
    }
}
