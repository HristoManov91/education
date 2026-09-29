package bg.hristomanov.education.eventsourcing.domain;

import java.math.BigDecimal;

public record MoneyWithdrawn(
        BigDecimal amount,
        String reference
) implements AccountEvent {
}
