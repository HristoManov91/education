package bg.hristomanov.education.patterns.structural.composite;

import java.math.BigDecimal;
import java.util.List;

/**
 * Composite позволява leaf и container objects да се третират през един interface.
 */
public record Bundle(
        String name,
        List<PriceComponent> children
) implements PriceComponent {

    public Bundle {
        children = List.copyOf(children);
    }

    @Override
    public BigDecimal total() {
        return children.stream()
                .map(PriceComponent::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
