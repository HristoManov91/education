package bg.hristomanov.education.patterns.creational.abstractfactory;

import java.math.BigDecimal;

public interface TaxCalculator {

    BigDecimal taxFor(BigDecimal netAmount);
}
