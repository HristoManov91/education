package bg.hristomanov.education.patterns.behavioral.state;

/**
 * State pattern премества поведението на lifecycle state-а в отделни objects,
 * вместо domain class-ът да расте с много {@code if/switch} проверки.
 */
public class Order {

    private OrderState state = new PendingOrderState();

    public String stateName() {
        return state.name();
    }

    public void pay() {
        state.pay(this);
    }

    public void ship() {
        state.ship(this);
    }

    public void cancel() {
        state.cancel(this);
    }

    void transitionTo(OrderState newState) {
        this.state = newState;
    }
}
