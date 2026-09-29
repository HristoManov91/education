package bg.hristomanov.education.hexagonal.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class Order {

    private final Long id;
    private final String reference;
    private final String customerId;
    private final OrderStatus status;
    private final List<OrderLine> lines;
    private final BigDecimal totalAmount;

    private Order(
            Long id,
            String reference,
            String customerId,
            OrderStatus status,
            List<OrderLine> lines
    ) {
        this.id = id;
        this.reference = Objects.requireNonNull(reference);
        this.customerId = Objects.requireNonNull(customerId);
        this.status = Objects.requireNonNull(status);
        this.lines = List.copyOf(lines);

        if (reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        if (customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line");
        }

        this.totalAmount = this.lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Order place(
            String reference,
            String customerId,
            List<OrderLine> lines
    ) {
        return new Order(
                null,
                reference,
                customerId,
                OrderStatus.PLACED,
                lines
        );
    }

    public static Order rehydrate(
            Long id,
            String reference,
            String customerId,
            OrderStatus status,
            List<OrderLine> lines
    ) {
        return new Order(
                Objects.requireNonNull(id),
                reference,
                customerId,
                status,
                lines
        );
    }

    public Order withId(long persistedId) {
        return new Order(
                persistedId,
                reference,
                customerId,
                status,
                lines
        );
    }

    public Long id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public String customerId() {
        return customerId;
    }

    public OrderStatus status() {
        return status;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }
}
