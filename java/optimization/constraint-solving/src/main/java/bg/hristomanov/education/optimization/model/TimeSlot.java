package bg.hristomanov.education.optimization.model;

import java.util.Objects;

/**
 * Input contract: slots form a non-overlapping time grid.
 * Arbitrary overlapping intervals need a different conflict predicate.
 */
public record TimeSlot(String id, int startMinute, int endMinute) {

    public TimeSlot {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || startMinute < 0 || endMinute <= startMinute || endMinute > 24 * 60) {
            throw new IllegalArgumentException("Invalid time slot");
        }
    }
}
