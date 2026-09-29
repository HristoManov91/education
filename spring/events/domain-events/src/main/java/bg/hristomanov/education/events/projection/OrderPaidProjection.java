package bg.hristomanov.education.events.projection;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "order_paid_projection",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paid_projection_order",
                columnNames = "order_id"
        )
)
public class OrderPaidProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_reference", nullable = false, length = 64)
    private String orderReference;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected OrderPaidProjection() {
    }

    public OrderPaidProjection(
            Long orderId,
            String orderReference,
            Instant observedAt
    ) {
        this.orderId = orderId;
        this.orderReference = orderReference;
        this.observedAt = observedAt;
    }

    public Long getOrderId() {
        return orderId;
    }
}
