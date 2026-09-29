package bg.hristomanov.education.distributedcache;

import java.math.BigDecimal;

public record Product(
        long id,
        String name,
        BigDecimal price,
        long version
) {
}
