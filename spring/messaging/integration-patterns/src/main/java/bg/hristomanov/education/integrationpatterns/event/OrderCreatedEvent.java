package bg.hristomanov.education.integrationpatterns.event;

import java.util.List;
import java.util.Objects;

/**
 * A hand-written example of the producer's JSON message model, deliberately
 * separate from broker code and generated-source tooling. The corresponding
 * AsyncAPI schema lives under src/main/resources/asyncapi/order-events.yaml.
 */
public record OrderCreatedEvent(String id, Source source, List<Item> items) {

    public OrderCreatedEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        items = List.copyOf(items);
    }

    public enum Source {
        CUSTOMER, WAREHOUSE
    }

    public record Item(String productId, int quantity) {
    }
}
