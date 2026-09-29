package bg.hristomanov.education.patterns.behavioral.mediator;

/**
 * Mediator централизира interaction rules между components.
 *
 * <p>PaymentComponent не знае InventoryComponent, а InventoryComponent не знае
 * PaymentComponent или ShippingComponent. Те уведомяват mediator-а, който
 * решава следващата coordination стъпка.</p>
 *
 * <p>Разликата спрямо Facade е важна: Facade обикновено предлага simplified
 * API на caller-а; Mediator управлява communication между peer components.</p>
 */
public class OrderWorkflowMediator implements WorkflowMediator {

    private final PaymentComponent payment;
    private final InventoryComponent inventory;
    private final ShippingComponent shipping;

    public OrderWorkflowMediator(
            PaymentComponent payment,
            InventoryComponent inventory,
            ShippingComponent shipping
    ) {
        this.payment = payment;
        this.inventory = inventory;
        this.shipping = shipping;

        payment.setMediator(this);
        inventory.setMediator(this);
    }

    @Override
    public void paymentCompleted(String orderId) {
        inventory.reserve(orderId);
    }

    @Override
    public void inventoryReserved(String orderId) {
        shipping.schedule(orderId);
    }

    @Override
    public void inventoryReservationFailed(String orderId) {
        payment.refund(orderId);
    }
}
