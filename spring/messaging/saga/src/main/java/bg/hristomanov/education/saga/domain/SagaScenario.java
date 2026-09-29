package bg.hristomanov.education.saga.domain;

public enum SagaScenario {
    HAPPY_PATH,
    PAYMENT_FAILURE,
    INVENTORY_FAILURE,
    SHIPPING_FAILURE,
    PAYMENT_COMPENSATION_FAILURE
}
