package bg.hristomanov.education.patterns.behavioral.mediator;

import java.util.List;

public class PaymentComponent {

    private final List<String> trace;
    private WorkflowMediator mediator;

    public PaymentComponent(List<String> trace) {
        this.trace = trace;
    }

    public void setMediator(WorkflowMediator mediator) {
        this.mediator = mediator;
    }

    public void complete(String orderId) {
        trace.add("payment:completed:" + orderId);
        mediator.paymentCompleted(orderId);
    }

    public void refund(String orderId) {
        trace.add("payment:refunded:" + orderId);
    }
}
