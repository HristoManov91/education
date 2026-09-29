package bg.hristomanov.education.patterns.behavioral.chain;

import java.math.BigDecimal;
import java.util.Optional;

public class PositiveTotalValidation extends OrderValidationHandler {

    @Override
    protected Optional<String> validateCurrent(OrderDraft order) {
        if (order.total() == null || order.total().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of("total must be positive");
        }
        return Optional.empty();
    }
}
