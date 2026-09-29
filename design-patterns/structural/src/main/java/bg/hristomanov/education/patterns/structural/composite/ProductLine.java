package bg.hristomanov.education.patterns.structural.composite;

import java.math.BigDecimal;

public record ProductLine(
        String sku,
        BigDecimal unitPrice,
        int quantity
) implements PriceComponent {

    @Override
    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
