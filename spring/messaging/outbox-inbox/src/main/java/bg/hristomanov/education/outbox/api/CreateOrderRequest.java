package bg.hristomanov.education.outbox.api;

import java.math.BigDecimal;

public record CreateOrderRequest(
        String reference,
        String customerId,
        BigDecimal totalAmount
) {
}
