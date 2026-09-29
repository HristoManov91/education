package bg.hristomanov.education.hexagonal.adapter.in.rest;

import bg.hristomanov.education.hexagonal.port.in.OrderView;

import java.math.BigDecimal;
import java.util.List;

public record OrderHttpResponse(
        long id,
        String reference,
        String customerId,
        String status,
        BigDecimal totalAmount,
        List<Line> lines
) {

    public static OrderHttpResponse from(OrderView view) {
        return new OrderHttpResponse(
                view.id(),
                view.reference(),
                view.customerId(),
                view.status(),
                view.totalAmount(),
                view.lines()
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

        private static Line from(OrderView.Line line) {
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
