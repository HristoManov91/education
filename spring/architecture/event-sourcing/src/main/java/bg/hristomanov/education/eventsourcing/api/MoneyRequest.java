package bg.hristomanov.education.eventsourcing.api;

import java.math.BigDecimal;

public record MoneyRequest(
        BigDecimal amount,
        String reference
) {
}
