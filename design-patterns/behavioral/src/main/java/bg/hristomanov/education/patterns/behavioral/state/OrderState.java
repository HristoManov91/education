package bg.hristomanov.education.patterns.behavioral.state;

public interface OrderState {

    String name();

    void pay(Order order);

    void ship(Order order);

    void cancel(Order order);
}
