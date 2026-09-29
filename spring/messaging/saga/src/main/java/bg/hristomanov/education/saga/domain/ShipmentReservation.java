package bg.hristomanov.education.saga.domain;

import jakarta.persistence.*;

@Entity
@Table(
        name = "shipment_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shipment_saga",
                columnNames = "saga_id"
        )
)
public class ShipmentReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private Long sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParticipantStatus status;

    protected ShipmentReservation() {
    }

    public ShipmentReservation(Long sagaId) {
        this.sagaId = sagaId;
        this.status = ParticipantStatus.SCHEDULED;
    }

    public void cancel() {
        this.status = ParticipantStatus.CANCELLED;
    }

    public Long getSagaId() {
        return sagaId;
    }

    public ParticipantStatus getStatus() {
        return status;
    }
}
