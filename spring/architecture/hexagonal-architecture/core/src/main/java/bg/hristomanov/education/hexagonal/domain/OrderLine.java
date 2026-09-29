package bg.hristomanov.education.hexagonal.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderLine(
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {

    public OrderLine {
        Objects.requireNonNull(sku);
        Objects.requireNonNull(productName);
        Objects.requireNonNull(unitPrice);

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
