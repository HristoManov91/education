package bg.hristomanov.education.patterns.behavioral.mediator;

public interface WorkflowMediator {

    void paymentCompleted(String orderId);

    void inventoryReserved(String orderId);

    void inventoryReservationFailed(String orderId);
}
