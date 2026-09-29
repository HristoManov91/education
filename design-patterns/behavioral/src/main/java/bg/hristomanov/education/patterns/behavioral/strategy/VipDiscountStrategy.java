package bg.hristomanov.education.patterns.behavioral.strategy;

import java.math.BigDecimal;

public class VipDiscountStrategy implements DiscountStrategy {

    private static final BigDecimal DISCOUNT_FACTOR = new BigDecimal("0.90");

    @Override
    public CustomerSegment supports() {
        return CustomerSegment.VIP;
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        return originalPrice.multiply(DISCOUNT_FACTOR);
    }
}
