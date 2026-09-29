package bg.hristomanov.education.cqrs.api;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        String reference,
        String customerId,
        List<Line> lines
) {
    public record Line(
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {
    }
}
