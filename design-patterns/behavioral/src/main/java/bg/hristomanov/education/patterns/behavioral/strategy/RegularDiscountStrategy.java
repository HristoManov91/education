package bg.hristomanov.education.patterns.behavioral.strategy;

import java.math.BigDecimal;

public class RegularDiscountStrategy implements DiscountStrategy {

    @Override
    public CustomerSegment supports() {
        return CustomerSegment.REGULAR;
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        return originalPrice;
    }
}
