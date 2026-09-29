package bg.hristomanov.education.patterns.behavioral.mediator;

import java.util.List;

public class InventoryComponent {

    private final boolean inventoryAvailable;
    private final List<String> trace;
    private WorkflowMediator mediator;

    public InventoryComponent(boolean inventoryAvailable, List<String> trace) {
        this.inventoryAvailable = inventoryAvailable;
        this.trace = trace;
    }

    public void setMediator(WorkflowMediator mediator) {
        this.mediator = mediator;
    }

    public void reserve(String orderId) {
        trace.add("inventory:attempt:" + orderId);

        if (inventoryAvailable) {
            trace.add("inventory:reserved:" + orderId);
            mediator.inventoryReserved(orderId);
        } else {
            trace.add("inventory:failed:" + orderId);
            mediator.inventoryReservationFailed(orderId);
        }
    }
}
