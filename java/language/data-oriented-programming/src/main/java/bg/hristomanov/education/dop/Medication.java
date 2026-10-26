package bg.hristomanov.education.dop;

import java.util.Objects;

public record Medication(Drug drug, Dose dose) {

    public Medication {
        Objects.requireNonNull(drug, "drug");
        Objects.requireNonNull(dose, "dose");
    }
}
