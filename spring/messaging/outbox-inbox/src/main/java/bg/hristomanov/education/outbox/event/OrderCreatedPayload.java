package bg.hristomanov.education.outbox.event;

import java.math.BigDecimal;

public record OrderCreatedPayload(
        long orderId,
        String orderReference,
        String customerId,
        BigDecimal totalAmount
) {
}
