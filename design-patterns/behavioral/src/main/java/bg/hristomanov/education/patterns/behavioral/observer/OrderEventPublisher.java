package bg.hristomanov.education.patterns.behavioral.observer;

import java.util.List;

/**
 * Observer: publisher-ът не знае конкретните reactions.
 *
 * <p>Spring {@code ApplicationEventPublisher} + {@code @EventListener} дава
 * framework-level вариант на подобна идея. Това не е автоматично distributed
 * messaging — in-process observer и Kafka/RabbitMQ pub/sub имат различни failure semantics.</p>
 */
public class OrderEventPublisher {

    private final List<OrderEventListener> listeners;

    public OrderEventPublisher(List<OrderEventListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    public void publish(OrderPlacedEvent event) {
        for (OrderEventListener listener : listeners) {
            listener.onOrderPlaced(event);
        }
    }
}
