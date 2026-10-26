package bg.hristomanov.education.aitesting.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class RegistrationPolicyTest {

    private final RegistrationPolicy policy = new RegistrationPolicy();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void exactlyEighteenYearsOldIsEligible() {
        assertThat(policy.evaluate(LocalDate.of(2008, 10, 26), true, clock))
                .isEqualTo(RegistrationPolicy.Decision.ELIGIBLE);
    }

    @Test
    void oneDayBeforeTheEighteenthBirthdayIsRejected() {
        assertThat(policy.evaluate(LocalDate.of(2008, 10, 27), true, clock))
                .isEqualTo(RegistrationPolicy.Decision.TOO_YOUNG);
    }

    @Test
    void missingConsentRejectsOtherwiseEligibleCustomer() {
        assertThat(policy.evaluate(LocalDate.of(1991, 1, 1), false, clock))
                .isEqualTo(RegistrationPolicy.Decision.CONSENT_REQUIRED);
    }

    @Test
    void futureBirthDateIsNotConsideredEligible() {
        assertThat(policy.evaluate(LocalDate.of(2030, 1, 1), true, clock))
                .isEqualTo(RegistrationPolicy.Decision.TOO_YOUNG);
    }

    @ParameterizedTest
    @CsvSource({"2007-10-26, ELIGIBLE", "2009-10-26, TOO_YOUNG",
            "1980-01-01, ELIGIBLE", "2026-10-26, TOO_YOUNG"})
    void fixedClockProducesConsistentAgeBasedDecisions(LocalDate birthday,
                                                         RegistrationPolicy.Decision expected) {
        assertThat(policy.evaluate(birthday, true, clock)).isEqualTo(expected);
    }

    @Test
    void nullDateFailsWithExplicitError() {
        assertThatNullPointerException()
                .isThrownBy(() -> policy.evaluate(null, true, clock))
                .withMessageContaining("birthDate");
    }
}
