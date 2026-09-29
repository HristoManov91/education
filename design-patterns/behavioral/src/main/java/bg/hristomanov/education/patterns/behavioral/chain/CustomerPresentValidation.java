package bg.hristomanov.education.patterns.behavioral.chain;

import java.util.Optional;

public class CustomerPresentValidation extends OrderValidationHandler {

    @Override
    protected Optional<String> validateCurrent(OrderDraft order) {
        if (order.customerId() == null || order.customerId().isBlank()) {
            return Optional.of("customer is required");
        }
        return Optional.empty();
    }
}
