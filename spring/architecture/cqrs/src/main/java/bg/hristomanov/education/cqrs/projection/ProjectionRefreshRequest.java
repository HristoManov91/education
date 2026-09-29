package bg.hristomanov.education.cqrs.projection;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "cqrs_projection_refresh",
        indexes = @Index(
                name = "idx_projection_refresh_pending",
                columnList = "processed_at,created_at"
        )
)
public class ProjectionRefreshRequest {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ProjectionRefreshRequest() {
    }

    public ProjectionRefreshRequest(Long orderId, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public void markProcessed(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
