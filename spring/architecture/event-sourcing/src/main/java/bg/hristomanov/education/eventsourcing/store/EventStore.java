package bg.hristomanov.education.eventsourcing.store;

import bg.hristomanov.education.eventsourcing.domain.AccountEvent;

import java.util.List;

public interface EventStore {

    EventStream readStream(String streamId);

    EventStream readStreamUpToVersion(
            String streamId,
            long inclusiveVersion
    );

    List<StoredEvent> readStreamAfterVersion(
            String streamId,
            long exclusiveVersion
    );

    List<StoredEvent> readAllAfterPosition(long exclusiveGlobalPosition);

    void append(
            String streamId,
            long expectedVersion,
            List<AccountEvent> newEvents
    );
}
