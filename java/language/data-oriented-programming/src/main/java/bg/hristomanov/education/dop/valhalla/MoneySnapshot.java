package bg.hristomanov.education.dop.valhalla;

import java.util.Objects;

/**
 * Java 25 record: immutable business value with explicit validation.
 * Това НЕ е JDK 28 value record: обикновените records запазват identity.
 */
public record MoneySnapshot(long cents, String currency) {

    public MoneySnapshot {
        currency = Objects.requireNonNull(currency, "currency");
        if (cents < 0) {
            throw new IllegalArgumentException("cents must be nonnegative");
        }
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO-like uppercase code");
        }
    }

    public MoneySnapshot plus(MoneySnapshot other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot combine different currencies");
        }
        return new MoneySnapshot(Math.addExact(cents, other.cents), currency);
    }

    public MoneySnapshot withCents(long newCents) {
        return new MoneySnapshot(newCents, currency);
    }
}
