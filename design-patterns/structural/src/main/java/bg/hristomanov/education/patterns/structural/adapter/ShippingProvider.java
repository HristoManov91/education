package bg.hristomanov.education.patterns.structural.adapter;

import java.math.BigDecimal;

public interface ShippingProvider {

    ShippingQuote quote(String postalCode, BigDecimal weightKg);
}
