package bg.hristomanov.education.patterns.structural.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Adapter превежда чужд interface/model към interface-а, който нашият domain очаква.
 *
 * <p>Consumer-ът не трябва да знае за grams/euro-cents или конкретния legacy client.</p>
 */
public class LegacyCourierAdapter implements ShippingProvider {

    private final LegacyCourierClient legacyClient;

    public LegacyCourierAdapter(LegacyCourierClient legacyClient) {
        this.legacyClient = legacyClient;
    }

    @Override
    public ShippingQuote quote(String postalCode, BigDecimal weightKg) {
        int weightGrams = weightKg
                .multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();

        int priceCents = legacyClient.calculatePriceInEuroCents(postalCode, weightGrams);
        BigDecimal priceEur = BigDecimal.valueOf(priceCents, 2);

        return new ShippingQuote("legacy-courier", priceEur);
    }
}
