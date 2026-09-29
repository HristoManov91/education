package bg.hristomanov.education.uow.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Version
    private long version;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String reference, BigDecimal totalAmount) {
        this.reference = Objects.requireNonNull(reference);
        this.totalAmount = Objects.requireNonNull(totalAmount);
        this.status = OrderStatus.NEW;
    }

    public void markPaid() {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Only NEW order can be paid");
        }
        status = OrderStatus.PAID;
    }

    public void cancel() {
        if (status == OrderStatus.PAID) {
            throw new IllegalStateException("Paid order cannot be cancelled in this demo");
        }
        status = OrderStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public long getVersion() {
        return version;
    }
}
