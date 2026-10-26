package bg.hristomanov.education.aitesting.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Pure business rule: no Spring, security filters or persistence.
 * The injected Clock makes boundary tests deterministic.
 */
public final class RegistrationPolicy {

    public enum Decision {
        ELIGIBLE,
        TOO_YOUNG,
        CONSENT_REQUIRED
    }

    public Decision evaluate(LocalDate birthDate, boolean acceptedTerms, Clock clock) {
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(clock, "clock");
        LocalDate today = LocalDate.now(clock);
        if (birthDate.isAfter(today) || Period.between(birthDate, today).getYears() < 18) {
            return Decision.TOO_YOUNG;
        }
        if (!acceptedTerms) {
            return Decision.CONSENT_REQUIRED;
        }
        return Decision.ELIGIBLE;
    }
}
