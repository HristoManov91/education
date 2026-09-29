package bg.hristomanov.education.cqrs.write;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(
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
