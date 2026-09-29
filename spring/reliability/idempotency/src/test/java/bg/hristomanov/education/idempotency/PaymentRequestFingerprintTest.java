package bg.hristomanov.education.idempotency;

import bg.hristomanov.education.idempotency.api.CreatePaymentRequest;
import bg.hristomanov.education.idempotency.service.PaymentRequestFingerprint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRequestFingerprintTest {

    private final PaymentRequestFingerprint fingerprint =
            new PaymentRequestFingerprint();

    @Test
    void semanticallyEquivalentPayloadsHaveTheSameFingerprint() {
        CreatePaymentRequest first = new CreatePaymentRequest(
                " C-42 ",
                new BigDecimal("120.00"),
                "eur"
        );
        CreatePaymentRequest second = new CreatePaymentRequest(
                "C-42",
                new BigDecimal("120.0"),
                "EUR"
        );

        assertThat(fingerprint.calculate(first))
                .isEqualTo(fingerprint.calculate(second));
    }

    @Test
    void materiallyDifferentPayloadHasDifferentFingerprint() {
        CreatePaymentRequest first = new CreatePaymentRequest(
                "C-42",
                new BigDecimal("120.00"),
                "EUR"
        );
        CreatePaymentRequest second = new CreatePaymentRequest(
                "C-42",
                new BigDecimal("121.00"),
                "EUR"
        );

        assertThat(fingerprint.calculate(first))
                .isNotEqualTo(fingerprint.calculate(second));
    }
}
