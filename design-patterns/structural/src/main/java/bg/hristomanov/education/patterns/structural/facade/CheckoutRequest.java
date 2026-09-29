package bg.hristomanov.education.patterns.structural.facade;

import java.math.BigDecimal;

public record CheckoutRequest(
        String customerId,
        String sku,
        int quantity,
        BigDecimal amount
) {
}
