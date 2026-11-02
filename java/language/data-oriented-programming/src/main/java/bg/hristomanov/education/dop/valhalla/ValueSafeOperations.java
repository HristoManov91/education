package bg.hristomanov.education.dop.valhalla;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Никакви identity-sensitive операции върху value-based JDK типове:
 * equals и обикновен HashMap, не == или IdentityHashMap.
 */
public final class ValueSafeOperations {

    private ValueSafeOperations() {
    }

    public static boolean sameBusinessDate(LocalDate first, LocalDate second) {
        return Objects.equals(first, second);
    }

    public static Map<LocalDate, Long> sumByDueDate(Iterable<PaymentSnapshot> payments) {
        Objects.requireNonNull(payments, "payments");
        Map<LocalDate, Long> totals = new HashMap<>();
        String batchCurrency = null;
        for (PaymentSnapshot payment : payments) {
            Objects.requireNonNull(payment, "payment");
            MoneySnapshot amount = payment.amount();
            // Сумиране само по дата е коректно единствено за едновалутен batch.
            // Иначе бихме събрали 100 EUR + 100 USD като 200 без единица.
            if (batchCurrency == null) {
                batchCurrency = amount.currency();
            } else if (!batchCurrency.equals(amount.currency())) {
                throw new IllegalArgumentException("Mixed currencies cannot share date-only totals");
            }
            totals.merge(payment.dueDate(), amount.cents(), Math::addExact);
        }
        return Map.copyOf(totals);
    }
}
