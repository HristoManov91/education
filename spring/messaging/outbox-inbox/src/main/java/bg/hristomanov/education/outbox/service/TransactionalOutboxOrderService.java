package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.domain.OrderEntity;
import bg.hristomanov.education.outbox.event.OrderCreatedPayload;
import bg.hristomanov.education.outbox.outbox.OutboxEvent;
import bg.hristomanov.education.outbox.repository.OrderRepository;
import bg.hristomanov.education.outbox.repository.OutboxEventRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transactional Outbox producer.
 *
 * <p>Business row + outbox event се записват в ЕДНА local DB transaction.
 * Broker publish се случва по-късно от отделен relay.</p>
 */
@Service
public class TransactionalOutboxOrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public TransactionalOutboxOrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxRepository,
            JsonMapper jsonMapper
    ) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public long createOrder(
            String reference,
            String customerId,
            BigDecimal totalAmount
    ) {
        return createOrderInternal(
                reference,
                customerId,
                totalAmount,
                false
        );
    }

    @Transactional
    public long createOrderAndFailBeforeCommit(
            String reference,
            String customerId,
            BigDecimal totalAmount
    ) {
        return createOrderInternal(
                reference,
                customerId,
                totalAmount,
                true
        );
    }

    private long createOrderInternal(
            String reference,
            String customerId,
            BigDecimal totalAmount,
            boolean failBeforeCommit
    ) {
        Instant now = Instant.now();

        OrderEntity order = orderRepository.saveAndFlush(
                new OrderEntity(
                        reference,
                        customerId,
                        totalAmount,
                        now
                )
        );

        OrderCreatedPayload payload = new OrderCreatedPayload(
                order.getId(),
                order.getReference(),
                order.getCustomerId(),
                order.getTotalAmount()
        );

        OutboxEvent event = new OutboxEvent(
                "Order",
                order.getId().toString(),
                "OrderCreated",
                serialize(payload),
                now
        );

        outboxRepository.save(event);

        if (failBeforeCommit) {
            throw new IllegalStateException(
                    "Simulated failure before local DB transaction commit"
            );
        }

        return order.getId();
    }

    private String serialize(OrderCreatedPayload payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize outbox payload", exception);
        }
    }
}
