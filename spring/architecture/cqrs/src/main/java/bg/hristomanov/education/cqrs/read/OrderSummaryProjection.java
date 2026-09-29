package bg.hristomanov.education.cqrs.read;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cqrs_order_summary")
public class OrderSummaryProjection {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 64)
    private String reference;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private int itemCount;

    @Column(nullable = false, length = 200)
    private String displayLabel;

    @Column(nullable = false)
    private long sourceVersion;

    @Column(nullable = false)
    private Instant projectedAt;

    protected OrderSummaryProjection() {
    }

    public OrderSummaryProjection(
            Long orderId,
            String reference,
            String customerId,
            String status,
            BigDecimal totalAmount,
            int itemCount,
            String displayLabel,
            long sourceVersion,
            Instant projectedAt
    ) {
        this.orderId = orderId;
        this.reference = reference;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
        this.displayLabel = displayLabel;
        this.sourceVersion = sourceVersion;
        this.projectedAt = projectedAt;
    }

    public void refresh(
            String status,
            BigDecimal totalAmount,
            int itemCount,
            String displayLabel,
            long sourceVersion,
            Instant projectedAt
    ) {
        this.status = status;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
        this.displayLabel = displayLabel;
        this.sourceVersion = sourceVersion;
        this.projectedAt = projectedAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getReference() {
        return reference;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public Instant getProjectedAt() {
        return projectedAt;
    }
}
