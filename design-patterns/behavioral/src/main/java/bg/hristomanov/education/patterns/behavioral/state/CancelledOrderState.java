package bg.hristomanov.education.patterns.behavioral.state;

public class CancelledOrderState implements OrderState {

    @Override
    public String name() {
        return "CANCELLED";
    }

    @Override
    public void pay(Order order) {
        throw new IllegalStateException("Cancelled order cannot be paid");
    }

    @Override
    public void ship(Order order) {
        throw new IllegalStateException("Cancelled order cannot be shipped");
    }

    @Override
    public void cancel(Order order) {
        throw new IllegalStateException("Order is already cancelled");
    }
}
