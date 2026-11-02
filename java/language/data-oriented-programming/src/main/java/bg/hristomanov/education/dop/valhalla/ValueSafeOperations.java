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
        for (PaymentSnapshot payment : payments) {
            Objects.requireNonNull(payment, "payment");
            totals.merge(payment.dueDate(), payment.amount().cents(), Math::addExact);
        }
        return Map.copyOf(totals);
    }
}
