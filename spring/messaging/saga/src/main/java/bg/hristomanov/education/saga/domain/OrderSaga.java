package bg.hristomanov.education.saga.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "order_sagas")
public class OrderSaga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SagaStatus status;

    @Column(length = 500)
    private String failureReason;

    protected OrderSaga() {
    }

    public OrderSaga(String orderReference) {
        this.orderReference = orderReference;
        this.status = SagaStatus.RUNNING;
    }

    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.failureReason = null;
    }

    public void startCompensation(String reason) {
        this.status = SagaStatus.COMPENSATING;
        this.failureReason = reason;
    }

    public void reject() {
        this.status = SagaStatus.REJECTED;
    }

    public void requireCompensationRetry(String reason) {
        this.status = SagaStatus.COMPENSATION_REQUIRED;
        this.failureReason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
