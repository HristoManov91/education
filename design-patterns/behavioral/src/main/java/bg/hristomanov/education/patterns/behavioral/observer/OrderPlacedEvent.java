package bg.hristomanov.education.patterns.behavioral.observer;

import java.math.BigDecimal;

public record OrderPlacedEvent(
        String orderId,
        String customerEmail,
        BigDecimal total
) {
}
