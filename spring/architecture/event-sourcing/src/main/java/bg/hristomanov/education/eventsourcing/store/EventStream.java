package bg.hristomanov.education.eventsourcing.store;

import java.util.List;

public record EventStream(
        String streamId,
        long version,
        List<StoredEvent> events
) {

    public EventStream {
        events = List.copyOf(events);
    }
}
