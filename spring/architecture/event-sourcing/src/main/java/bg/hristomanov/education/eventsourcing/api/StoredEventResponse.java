package bg.hristomanov.education.eventsourcing.api;

import bg.hristomanov.education.eventsourcing.store.StoredEvent;

import java.time.Instant;

public record StoredEventResponse(
        long globalPosition,
        String eventId,
        long streamVersion,
        String eventType,
        int eventSchemaVersion,
        Instant occurredAt,
        Object payload
) {

    public static StoredEventResponse from(
            StoredEvent event
    ) {
        return new StoredEventResponse(
                event.globalPosition(),
                event.eventId(),
                event.streamVersion(),
                event.eventType(),
                event.eventSchemaVersion(),
                event.occurredAt(),
                event.event()
        );
    }
}
