package bg.hristomanov.education.events.integration;

import bg.hristomanov.education.events.domain.OrderPaid;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Domain Event и Integration Event не са задължително един и същ contract.
 *
 * <p>Integration event-ът има transport/public contract concerns като event ID
 * и schema version, които не са непременно част от domain language-а.</p>
 */
@Component
public class OrderPaidIntegrationEventMapper {

    public OrderPaidIntegrationEvent map(OrderPaid event) {
        return new OrderPaidIntegrationEvent(
                UUID.randomUUID().toString(),
                1,
                event.orderId(),
                event.orderReference(),
                event.occurredAt()
        );
    }
}
