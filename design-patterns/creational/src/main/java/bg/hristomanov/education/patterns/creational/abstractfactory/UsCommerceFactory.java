package bg.hristomanov.education.patterns.creational.abstractfactory;

import java.math.BigDecimal;

public class UsCommerceFactory implements CommerceFactory {

    @Override
    public TaxCalculator createTaxCalculator() {
        return netAmount -> netAmount.multiply(new BigDecimal("0.07"));
    }

    @Override
    public PaymentGateway createPaymentGateway() {
        return amount -> "US-USD:" + amount;
    }
}
