package bg.hristomanov.education.dop;

import java.util.List;
import java.util.Objects;

/**
 * Records are only shallowly immutable: make an explicit defensive copy of
 * mutable input collections and disallow null elements.
 */
public record Prescription(String patientId, List<Medication> medications) {

    public Prescription {
        Objects.requireNonNull(patientId, "patientId");
        if (patientId.isBlank()) {
            throw new IllegalArgumentException("patientId is required");
        }
        medications = List.copyOf(medications);
        if (medications.isEmpty()) {
            throw new IllegalArgumentException("At least one medication is required");
        }
    }
}
