package bg.hristomanov.education.patterns.behavioral.strategy;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy премества вариращия algorithm зад общ contract.
 *
 * <p>Това е типичен Spring pattern: вместо голям switch често inject-ваме
 * {@code List<DiscountStrategy>} и строим registry по type/key.</p>
 */
public class DiscountCalculator {

    private final Map<CustomerSegment, DiscountStrategy> strategies;

    public DiscountCalculator(List<DiscountStrategy> strategies) {
        EnumMap<CustomerSegment, DiscountStrategy> registry = new EnumMap<>(CustomerSegment.class);
        for (DiscountStrategy strategy : strategies) {
            registry.put(strategy.supports(), strategy);
        }
        this.strategies = Map.copyOf(registry);
    }

    public BigDecimal finalPrice(CustomerSegment segment, BigDecimal originalPrice) {
        DiscountStrategy strategy = strategies.get(segment);
        if (strategy == null) {
            throw new IllegalArgumentException("No discount strategy for " + segment);
        }
        return strategy.apply(originalPrice);
    }
}
