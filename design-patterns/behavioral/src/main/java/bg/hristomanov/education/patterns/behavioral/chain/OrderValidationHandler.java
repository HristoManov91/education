package bg.hristomanov.education.patterns.behavioral.chain;

import java.util.Optional;

/**
 * Chain of Responsibility прекарва request през последователност от handlers.
 *
 * <p>Всеки handler решава само своята проверка и при успех предава нататък.
 * Това е близко до servlet/security filter chains и validation pipelines.</p>
 */
public abstract class OrderValidationHandler {

    private OrderValidationHandler next;

    public OrderValidationHandler then(OrderValidationHandler nextHandler) {
        this.next = nextHandler;
        return nextHandler;
    }

    public final ValidationResult validate(OrderDraft order) {
        Optional<String> error = validateCurrent(order);
        if (error.isPresent()) {
            return ValidationResult.invalid(error.get());
        }

        if (next == null) {
            return ValidationResult.valid();
        }

        return next.validate(order);
    }

    protected abstract Optional<String> validateCurrent(OrderDraft order);
}
