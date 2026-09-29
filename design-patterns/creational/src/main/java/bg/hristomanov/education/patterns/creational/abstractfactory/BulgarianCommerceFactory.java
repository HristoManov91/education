package bg.hristomanov.education.patterns.creational.abstractfactory;

import java.math.BigDecimal;

public class BulgarianCommerceFactory implements CommerceFactory {

    @Override
    public TaxCalculator createTaxCalculator() {
        return netAmount -> netAmount.multiply(new BigDecimal("0.20"));
    }

    @Override
    public PaymentGateway createPaymentGateway() {
        return amount -> "BG-EUR:" + amount;
    }
}
