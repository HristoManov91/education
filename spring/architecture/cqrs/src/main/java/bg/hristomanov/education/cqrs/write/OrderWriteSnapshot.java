package bg.hristomanov.education.cqrs.write;

import java.math.BigDecimal;

public record OrderWriteSnapshot(
        long orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        int lineCount,
        long version
) {
}
