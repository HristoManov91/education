package bg.hristomanov.education.dop.valhalla;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValhallaReadinessTest {

    @Test
    void recordEqualityDescribesBusinessValueWithoutReferenceIdentityAssumptions() {
        MoneySnapshot first = new MoneySnapshot(500, "EUR");
        MoneySnapshot second = new MoneySnapshot(500, "EUR");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        // За разлика от equals, == не описва бизнес равенство.
    }

    @Test
    void recreatingImmutableSnapshotPreservesOriginalState() {
        PaymentSnapshot original = new PaymentSnapshot(42,
                new MoneySnapshot(500, "EUR"), LocalDate.of(2026, 11, 2));
        PaymentSnapshot revised = original.withAmount(new MoneySnapshot(750, "EUR"));
        assertThat(revised.amount().cents()).isEqualTo(750);
        assertThat(original.amount().cents()).isEqualTo(500);
        assertThat(revised.paymentId()).isEqualTo(original.paymentId());
        assertThat(revised.dueDate()).isEqualTo(original.dueDate());
        assertThat(original.withDueDate(LocalDate.of(2026, 11, 3)).dueDate())
                .isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(original.dueDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void separatelyCreatedEqualDatesUseSameBusinessBucket() {
        LocalDate first = LocalDate.of(2026, 11, 2);
        LocalDate second = LocalDate.of(2026, 11, 2);
        assertThat(ValueSafeOperations.sameBusinessDate(first, second)).isTrue();

        Map<LocalDate, Long> totals = ValueSafeOperations.sumByDueDate(List.of(
                new PaymentSnapshot(1, new MoneySnapshot(500, "EUR"), first),
                new PaymentSnapshot(2, new MoneySnapshot(250, "EUR"), second)));
        assertThat(totals).containsOnlyKeys(first);
        assertThat(totals.get(second)).isEqualTo(750L);
        assertThatThrownBy(() -> totals.put(first, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dateOnlyTotalsRejectMixedCurrenciesInsteadOfSilentlyAddingAmounts() {
        LocalDate date = LocalDate.of(2026, 11, 2);
        PaymentSnapshot euro = new PaymentSnapshot(1, new MoneySnapshot(500, "EUR"), date);
        PaymentSnapshot dollar = new PaymentSnapshot(2, new MoneySnapshot(200, "USD"), date);

        assertThatThrownBy(() -> ValueSafeOperations.sumByDueDate(List.of(euro, dollar)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mixed currencies");
    }

    @Test
    void validationsAndCurrencyBoundariesSurviveCopies() {
        MoneySnapshot amount = new MoneySnapshot(100, "EUR");
        PaymentSnapshot payment = new PaymentSnapshot(1, amount, LocalDate.of(2026, 11, 2));
        assertThatThrownBy(() -> amount.withCents(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.withAmount(new MoneySnapshot(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MoneySnapshot(1, "eUr")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentSnapshot(0, amount, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkedArithmeticPreventsSilentOverflow() {
        MoneySnapshot biggest = new MoneySnapshot(Long.MAX_VALUE, "EUR");
        assertThatThrownBy(() -> biggest.plus(new MoneySnapshot(1, "EUR")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> ValueSafeOperations.sumByDueDate(List.of(
                new PaymentSnapshot(1, biggest, LocalDate.of(2026, 11, 2)),
                new PaymentSnapshot(2, new MoneySnapshot(1, "EUR"), LocalDate.of(2026, 11, 2)))))
                .isInstanceOf(ArithmeticException.class);
        assertThat(biggest.plus(new MoneySnapshot(0, "EUR"))).isEqualTo(biggest);
    }
}
