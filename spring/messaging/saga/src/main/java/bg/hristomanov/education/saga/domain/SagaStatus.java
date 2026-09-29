package bg.hristomanov.education.saga.domain;

public enum SagaStatus {
    RUNNING,
    COMPLETED,
    COMPENSATING,
    REJECTED,
    COMPENSATION_REQUIRED
}
