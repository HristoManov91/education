package bg.hristomanov.education.instrumentation.sample;

/**
 * Business кодът НЕ знае, че съществува Java Agent.
 * Проверяваме еднакъв резултат при старт с и без -javaagent.
 */
public final class PricingService {

    public long quoteCents(int quantity, long unitPriceCents) {
        if (quantity < 0 || unitPriceCents < 0) {
            throw new IllegalArgumentException("quantity and unit price must be non-negative");
        }
        return Math.multiplyExact((long) quantity, unitPriceCents);
    }
}
