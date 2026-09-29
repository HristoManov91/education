package bg.hristomanov.education.patterns.behavioral.state;

public class PaidOrderState implements OrderState {

    @Override
    public String name() {
        return "PAID";
    }

    @Override
    public void pay(Order order) {
        throw new IllegalStateException("Order is already paid");
    }

    @Override
    public void ship(Order order) {
        order.transitionTo(new ShippedOrderState());
    }

    @Override
    public void cancel(Order order) {
        order.transitionTo(new CancelledOrderState());
    }
}
