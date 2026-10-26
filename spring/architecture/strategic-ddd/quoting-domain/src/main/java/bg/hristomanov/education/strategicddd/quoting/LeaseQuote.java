package bg.hristomanov.education.strategicddd.quoting;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pricing decision made from Quoting's own model and tariff data.
 */
public record LeaseQuote(VehicleConfiguration configuration, BigDecimal monthlyEur) {

    public LeaseQuote {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(monthlyEur, "monthlyEur");
        if (monthlyEur.signum() < 0) {
            throw new IllegalArgumentException("A quote cannot have negative monthly price");
        }
    }
}
