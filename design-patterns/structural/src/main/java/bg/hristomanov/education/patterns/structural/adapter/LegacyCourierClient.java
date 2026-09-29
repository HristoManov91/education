package bg.hristomanov.education.patterns.structural.adapter;

/**
 * Представяме си legacy/third-party API, което не контролираме.
 *
 * <p>То работи с грамове и връща цена в евроцентове, докато нашият domain
 * contract използва килограми и {@code BigDecimal} EUR.</p>
 */
public class LegacyCourierClient {

    public int calculatePriceInEuroCents(String zipCode, int weightGrams) {
        int baseCents = 500;
        int weightCents = Math.max(0, weightGrams / 100);
        return baseCents + weightCents;
    }
}
