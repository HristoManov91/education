package bg.hristomanov.education.hexagonal.port.in;

import bg.hristomanov.education.hexagonal.domain.Order;
import bg.hristomanov.education.hexagonal.domain.OrderLine;

import java.math.BigDecimal;
import java.util.List;

public record OrderView(
        long id,
        String reference,
        String customerId,
        String status,
        BigDecimal totalAmount,
        List<Line> lines
) {

    public static OrderView from(Order order) {
        if (order.id() == null) {
            throw new IllegalStateException("Persisted order must have an id");
        }

        return new OrderView(
                order.id(),
                order.reference(),
                order.customerId(),
                order.status().name(),
                order.totalAmount(),
                order.lines()
                        .stream()
                        .map(Line::from)
                        .toList()
        );
    }

    public record Line(
            String sku,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {

        private static Line from(OrderLine line) {
            return new Line(
                    line.sku(),
                    line.productName(),
                    line.quantity(),
                    line.unitPrice(),
                    line.lineTotal()
            );
        }
    }
}
