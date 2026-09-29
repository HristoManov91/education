package bg.hristomanov.education.saga.domain;

import jakarta.persistence.*;

@Entity
@Table(
        name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_saga",
                columnNames = "saga_id"
        )
)
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private Long sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParticipantStatus status;

    protected InventoryReservation() {
    }

    public InventoryReservation(Long sagaId) {
        this.sagaId = sagaId;
        this.status = ParticipantStatus.RESERVED;
    }

    public void release() {
        this.status = ParticipantStatus.RELEASED;
    }

    public Long getSagaId() {
        return sagaId;
    }

    public ParticipantStatus getStatus() {
        return status;
    }
}
