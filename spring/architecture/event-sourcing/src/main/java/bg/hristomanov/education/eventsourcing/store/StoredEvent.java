package bg.hristomanov.education.eventsourcing.store;

import bg.hristomanov.education.eventsourcing.domain.AccountEvent;

import java.time.Instant;

public record StoredEvent(
        long globalPosition,
        String eventId,
        String streamId,
        long streamVersion,
        String eventType,
        int eventSchemaVersion,
        Instant occurredAt,
        AccountEvent event
) {
}
