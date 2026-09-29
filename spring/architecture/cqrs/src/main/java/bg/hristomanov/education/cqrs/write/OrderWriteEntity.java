package bg.hristomanov.education.cqrs.write;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "cqrs_orders")
public class OrderWriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    @OneToMany(
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JoinColumn(name = "order_id", nullable = false)
    @OrderColumn(name = "line_order")
    private List<OrderLineEntity> lines = new ArrayList<>();

    protected OrderWriteEntity() {
    }

    public OrderWriteEntity(
            String reference,
            String customerId,
            List<OrderLineEntity> lines,
            Instant createdAt
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line");
        }

        this.reference = Objects.requireNonNull(reference);
        this.customerId = Objects.requireNonNull(customerId);
        this.status = OrderStatus.NEW;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lines.addAll(lines);
        this.totalAmount = calculateTotal(lines);
    }

    public void markPaid() {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Only NEW order can be paid");
        }
        status = OrderStatus.PAID;
    }

    private BigDecimal calculateTotal(List<OrderLineEntity> sourceLines) {
        return sourceLines.stream()
                .map(OrderLineEntity::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public List<OrderLineEntity> getLines() {
        return List.copyOf(lines);
    }
}
