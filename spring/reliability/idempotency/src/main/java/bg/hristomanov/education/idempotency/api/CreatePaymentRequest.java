package bg.hristomanov.education.idempotency.api;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        String customerId,
        BigDecimal amount,
        String currency
) {
}
