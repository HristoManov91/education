package bg.hristomanov.education.patterns.structural.facade;

/**
 * Facade дава един use-case oriented interface върху няколко subsystem-а.
 *
 * <p>Controller-ът не трябва да знае точния orchestration order между inventory,
 * payment и shipping. Това не означава, че facade-ът магически решава distributed
 * transaction/compensation проблемите — само поставя ясна boundary.</p>
 */
public class CheckoutFacade {

    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;

    public CheckoutFacade(
            InventoryService inventoryService,
            PaymentService paymentService,
            ShippingService shippingService
    ) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.shippingService = shippingService;
    }

    public CheckoutResult checkout(CheckoutRequest request) {
        String reservationId = inventoryService.reserve(request.sku(), request.quantity());
        String paymentId = paymentService.charge(request.customerId(), request.amount());
        String shipmentId = shippingService.schedule(reservationId);

        return new CheckoutResult(reservationId, paymentId, shipmentId);
    }
}
