package bg.hristomanov.education.aitesting.domain;

import java.time.LocalDate;
import java.util.Objects;

public record RegistrationCommand(String email, LocalDate birthDate, boolean acceptedTerms) {
    public RegistrationCommand {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(birthDate, "birthDate");
    }
}
