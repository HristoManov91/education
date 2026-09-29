package bg.hristomanov.education.patterns.behavioral.mediator;

import java.util.List;

public class ShippingComponent {

    private final List<String> trace;

    public ShippingComponent(List<String> trace) {
        this.trace = trace;
    }

    public void schedule(String orderId) {
        trace.add("shipping:scheduled:" + orderId);
    }
}
