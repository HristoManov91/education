package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.broker.BrokerMessage;
import bg.hristomanov.education.outbox.broker.InMemoryMessageBroker;
import bg.hristomanov.education.outbox.domain.OrderEntity;
import bg.hristomanov.education.outbox.event.OrderCreatedPayload;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * BAD baseline: първо commit-ваме DB, после публикуваме message.
 *
 * <p>Ако process execution прекъсне между тези две стъпки, order-ът остава
 * записан, а event-ът никога не достига broker-а.</p>
 */
@Service
public class NaiveOrderService {

    private final NaiveOrderPersistenceService persistenceService;
    private final InMemoryMessageBroker broker;
    private final JsonMapper jsonMapper;

    public NaiveOrderService(
            NaiveOrderPersistenceService persistenceService,
            InMemoryMessageBroker broker,
            JsonMapper jsonMapper
    ) {
        this.persistenceService = persistenceService;
        this.broker = broker;
        this.jsonMapper = jsonMapper;
    }

    public long createOrder(
            String reference,
            String customerId,
            BigDecimal totalAmount,
            boolean stopAfterDatabaseCommit
    ) {
        OrderEntity order = persistenceService.create(
                reference,
                customerId,
                totalAmount
        );

        if (stopAfterDatabaseCommit) {
            throw new IllegalStateException(
                    "Simulated interruption after DB commit and before broker publish"
            );
        }

        OrderCreatedPayload payload = new OrderCreatedPayload(
                order.getId(),
                order.getReference(),
                order.getCustomerId(),
                order.getTotalAmount()
        );

        broker.publish(
                new BrokerMessage(
                        UUID.randomUUID().toString(),
                        "Order",
                        order.getId().toString(),
                        "OrderCreated",
                        serialize(payload)
                )
        );

        return order.getId();
    }

    private String serialize(OrderCreatedPayload payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize order event", exception);
        }
    }
}
