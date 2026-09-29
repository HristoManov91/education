package bg.hristomanov.education.cache.domain;

import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        BigDecimal price
) {
}
