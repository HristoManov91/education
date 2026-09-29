package bg.hristomanov.education.patterns.behavioral.state;

public class PendingOrderState implements OrderState {

    @Override
    public String name() {
        return "PENDING";
    }

    @Override
    public void pay(Order order) {
        order.transitionTo(new PaidOrderState());
    }

    @Override
    public void ship(Order order) {
        throw new IllegalStateException("Pending order cannot be shipped");
    }

    @Override
    public void cancel(Order order) {
        order.transitionTo(new CancelledOrderState());
    }
}
