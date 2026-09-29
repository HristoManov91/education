package bg.hristomanov.education.outbox.broker;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Deterministic broker fixture.
 *
 * <p>Това не се представя като Kafka replacement. Целта е да можем надеждно
 * да симулираме crash window-а "publish succeeded, process crashed before
 * marking outbox row as published".</p>
 */
@Component
public class InMemoryMessageBroker {

    private final CopyOnWriteArrayList<BrokerMessage> published =
            new CopyOnWriteArrayList<>();

    public void publish(BrokerMessage message) {
        published.add(message);
    }

    public List<BrokerMessage> messages() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }
}
