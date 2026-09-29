package bg.hristomanov.education.patterns.behavioral.mediator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediatorPatternTest {

    @Test
    void mediatorCoordinatesSuccessWithoutComponentsKnowingEachOther() {
        List<String> trace = new ArrayList<>();
        PaymentComponent payment = new PaymentComponent(trace);
        InventoryComponent inventory = new InventoryComponent(true, trace);
        ShippingComponent shipping = new ShippingComponent(trace);
        new OrderWorkflowMediator(payment, inventory, shipping);

        payment.complete("O-42");

        assertThat(trace).containsExactly(
                "payment:completed:O-42",
                "inventory:attempt:O-42",
                "inventory:reserved:O-42",
                "shipping:scheduled:O-42"
        );
    }

    @Test
    void mediatorCoordinatesCompensationWhenInventoryFails() {
        List<String> trace = new ArrayList<>();
        PaymentComponent payment = new PaymentComponent(trace);
        InventoryComponent inventory = new InventoryComponent(false, trace);
        ShippingComponent shipping = new ShippingComponent(trace);
        new OrderWorkflowMediator(payment, inventory, shipping);

        payment.complete("O-43");

        assertThat(trace).containsExactly(
                "payment:completed:O-43",
                "inventory:attempt:O-43",
                "inventory:failed:O-43",
                "payment:refunded:O-43"
        );
    }
}
