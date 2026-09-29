package bg.hristomanov.education.eventsourcing.domain;

import java.math.BigDecimal;

public record MoneyDeposited(
        BigDecimal amount,
        String reference
) implements AccountEvent {
}
