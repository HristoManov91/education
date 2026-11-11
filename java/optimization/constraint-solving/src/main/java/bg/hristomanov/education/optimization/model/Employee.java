package bg.hristomanov.education.optimization.model;

import java.util.Objects;
import java.util.Set;

/** Immutable problem fact. All availability and skill lookups are in-memory. */
public record Employee(String id, Set<String> skills, Set<String> availableSlots,
                       Set<String> preferredSlots, int homeZone) {

    public Employee {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(availableSlots, "availableSlots");
        Objects.requireNonNull(preferredSlots, "preferredSlots");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Employee id must not be blank");
        }
        skills = Set.copyOf(skills);
        availableSlots = Set.copyOf(availableSlots);
        preferredSlots = Set.copyOf(preferredSlots);
    }
}
