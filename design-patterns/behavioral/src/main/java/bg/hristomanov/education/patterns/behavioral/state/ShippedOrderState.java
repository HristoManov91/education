package bg.hristomanov.education.patterns.behavioral.state;

public class ShippedOrderState implements OrderState {

    @Override
    public String name() {
        return "SHIPPED";
    }

    @Override
    public void pay(Order order) {
        throw new IllegalStateException("Shipped order cannot be paid again");
    }

    @Override
    public void ship(Order order) {
        throw new IllegalStateException("Order is already shipped");
    }

    @Override
    public void cancel(Order order) {
        throw new IllegalStateException("Shipped order cannot be cancelled");
    }
}
