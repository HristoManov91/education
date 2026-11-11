package bg.hristomanov.education.optimization.model;

import java.util.Objects;

/**
 * One planning value combines employee and timeslot. A single @PlanningVariable
 * avoids inconsistent half-assignments; it is one of several valid domain models.
 */
public record AssignmentOption(String id, Employee employee, TimeSlot slot) {

    public AssignmentOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(slot, "slot");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Assignment option id must not be blank");
        }
    }
}
