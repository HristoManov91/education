package bg.hristomanov.education.events.integration;

import java.time.Instant;

public record OrderPaidIntegrationEvent(
        String eventId,
        int schemaVersion,
        long orderId,
        String orderReference,
        Instant occurredAt
) {
}
