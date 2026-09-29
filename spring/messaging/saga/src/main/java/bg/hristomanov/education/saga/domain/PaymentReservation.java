package bg.hristomanov.education.saga.domain;

import jakarta.persistence.*;

@Entity
@Table(
        name = "payment_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_saga",
                columnNames = "saga_id"
        )
)
public class PaymentReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private Long sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParticipantStatus status;

    protected PaymentReservation() {
    }

    public PaymentReservation(Long sagaId) {
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
