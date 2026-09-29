package bg.hristomanov.education.patterns.creational.factorymethod;

import java.math.BigDecimal;

public record PaymentRequest(
        String reference,
        BigDecimal amount
) {
}
