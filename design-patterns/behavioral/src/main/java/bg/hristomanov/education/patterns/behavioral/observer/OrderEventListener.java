package bg.hristomanov.education.patterns.behavioral.observer;

public interface OrderEventListener {

    void onOrderPlaced(OrderPlacedEvent event);
}
