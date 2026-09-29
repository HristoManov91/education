package bg.hristomanov.education.events.listener;

import bg.hristomanov.education.events.domain.OrderPaid;
import bg.hristomanov.education.events.integration.OrderPaidIntegrationEvent;
import bg.hristomanov.education.events.integration.OrderPaidIntegrationEventMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transaction-bound reactions.
 *
 * <p>AFTER_COMMIT е default phase, но тук го пишем explicit за учебна яснота.</p>
 */
@Component
public class TransactionalOrderPaidListeners {

    private final EventObservationProbe probe;
    private final OrderPaidProjectionWriter projectionWriter;
    private final OrderPaidIntegrationEventMapper integrationEventMapper;

    public TransactionalOrderPaidListeners(
            EventObservationProbe probe,
            OrderPaidProjectionWriter projectionWriter,
            OrderPaidIntegrationEventMapper integrationEventMapper
    ) {
        this.probe = probe;
        this.projectionWriter = projectionWriter;
        this.integrationEventMapper = integrationEventMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Order(0)
    public void afterCommit(OrderPaid event) {
        probe.recordAfterCommit(event);
        projectionWriter.write(event);

        /*
         * Това е само mapping demonstration.
         * Не публикуваме директно към Kafka тук и не твърдим durability.
         * За reliable externalization използваме Outbox/Event Publication Registry.
         */
        OrderPaidIntegrationEvent integrationEvent =
                integrationEventMapper.map(event);
        probe.recordIntegrationEvent(integrationEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Order(0)
    public void afterRollback(OrderPaid event) {
        probe.recordAfterRollback(event);
    }
}
