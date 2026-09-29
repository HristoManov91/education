package bg.hristomanov.education.cqrs.read;

import java.math.BigDecimal;

public record OrderSummaryDto(
        long orderId,
        String reference,
        String customerId,
        String status,
        BigDecimal totalAmount,
        int itemCount,
        String displayLabel,
        long sourceVersion
) {

    public static OrderSummaryDto from(OrderSummaryProjection projection) {
        return new OrderSummaryDto(
                projection.getOrderId(),
                projection.getReference(),
                projection.getCustomerId(),
                projection.getStatus(),
                projection.getTotalAmount(),
                projection.getItemCount(),
                projection.getDisplayLabel(),
                projection.getSourceVersion()
        );
    }
}
