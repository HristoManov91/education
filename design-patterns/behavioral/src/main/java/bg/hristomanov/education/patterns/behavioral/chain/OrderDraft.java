package bg.hristomanov.education.patterns.behavioral.chain;

import java.math.BigDecimal;

public record OrderDraft(
        String customerId,
        String countryCode,
        BigDecimal total
) {
}
