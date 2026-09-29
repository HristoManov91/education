package bg.hristomanov.education.patterns.behavioral.strategy;

import java.math.BigDecimal;

public interface DiscountStrategy {

    CustomerSegment supports();

    BigDecimal apply(BigDecimal originalPrice);
}
