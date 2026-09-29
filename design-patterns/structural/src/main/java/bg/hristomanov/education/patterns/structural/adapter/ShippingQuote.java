package bg.hristomanov.education.patterns.structural.adapter;

import java.math.BigDecimal;

public record ShippingQuote(
        String provider,
        BigDecimal priceEur
) {
}
