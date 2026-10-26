package bg.hristomanov.education.strategicddd.quoting;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Quoting owns its tariff and configuration decisions.
 * Demo tariffs are deliberately local; a real system would use an owned
 * price catalog or a versioned, independently maintained read projection.
 */
public final class QuotingService {

    private static final Map<String, Tariff> TARIFFS = Map.of(
            "EV-TOURING", new Tariff(new BigDecimal("399.00"), Map.of(
                    "WINTER", new BigDecimal("25.00"),
                    "PANORAMA", new BigDecimal("38.00"))),
            "CITY-HYBRID", new Tariff(new BigDecimal("259.00"), Map.of(
                    "SAFETY", new BigDecimal("19.00"),
                    "COMFORT", new BigDecimal("21.00")))
    );

    public LeaseQuote quote(VehicleConfiguration configuration) {
        Tariff tariff = TARIFFS.get(configuration.modelCode());
        if (tariff == null) {
            throw new IllegalArgumentException("Unknown configurable model");
        }

        BigDecimal monthlyPrice = tariff.baseMonthlyEur();
        for (String optionCode : configuration.optionCodes()) {
            BigDecimal optionPrice = tariff.optionMonthlyEur().get(optionCode);
            if (optionPrice == null) {
                throw new IllegalArgumentException("Option not offered for this model: " + optionCode);
            }
            monthlyPrice = monthlyPrice.add(optionPrice);
        }
        return new LeaseQuote(configuration, monthlyPrice);
    }

    private record Tariff(BigDecimal baseMonthlyEur, Map<String, BigDecimal> optionMonthlyEur) {
    }
}
