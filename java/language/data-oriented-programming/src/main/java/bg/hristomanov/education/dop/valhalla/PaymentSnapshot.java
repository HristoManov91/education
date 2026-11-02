package bg.hristomanov.education.dop.valhalla;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Нова версия на immutable business state, вместо мутация на старата.
 * Java 25 еквивалент на идеята за derived record creation (proposal).
 */
public record PaymentSnapshot(long paymentId, MoneySnapshot amount, LocalDate dueDate) {

    public PaymentSnapshot {
        if (paymentId <= 0) {
            throw new IllegalArgumentException("paymentId must be positive");
        }
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(dueDate, "dueDate");
    }

    public PaymentSnapshot withAmount(MoneySnapshot newAmount) {
        Objects.requireNonNull(newAmount, "newAmount");
        if (!amount.currency().equals(newAmount.currency())) {
            throw new IllegalArgumentException("Currency change requires a distinct business operation");
        }
        return new PaymentSnapshot(paymentId, newAmount, dueDate);
    }

    public PaymentSnapshot withDueDate(LocalDate newDueDate) {
        return new PaymentSnapshot(paymentId, amount, newDueDate);
    }
}
