package bg.hristomanov.education.hexagonal.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Product(
        String sku,
        String name,
        BigDecimal unitPrice
) {

    public Product {
        Objects.requireNonNull(sku);
        Objects.requireNonNull(name);
        Objects.requireNonNull(unitPrice);

        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
    }
}
