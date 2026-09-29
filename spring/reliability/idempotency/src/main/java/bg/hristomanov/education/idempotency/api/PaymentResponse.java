package bg.hristomanov.education.idempotency.api;

import bg.hristomanov.education.idempotency.domain.Payment;
import bg.hristomanov.education.idempotency.domain.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(
        long paymentId,
        String customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        boolean replayed
) {

    public static PaymentResponse from(Payment payment, boolean replayed) {
        return new PaymentResponse(
                payment.getId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                replayed
        );
    }
}
