package bg.hristomanov.education.eventsourcing.store;

import bg.hristomanov.education.eventsourcing.domain.Account;
import bg.hristomanov.education.eventsourcing.domain.AccountEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JpaEventStore implements EventStore {

    private final StoredEventRepository repository;
    private final AccountEventCodec codec;

    public JpaEventStore(
            StoredEventRepository repository,
            AccountEventCodec codec
    ) {
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    @Transactional(readOnly = true)
    public EventStream readStream(String streamId) {
        List<StoredEvent> events = repository
                .findByStreamIdOrderByStreamVersionAsc(streamId)
                .stream()
                .map(this::toStoredEvent)
                .toList();

        long version = events.isEmpty()
                ? Account.NO_STREAM_VERSION
                : events.getLast().streamVersion();

        return new EventStream(streamId, version, events);
    }

    @Override
    @Transactional(readOnly = true)
    public EventStream readStreamUpToVersion(
            String streamId,
            long inclusiveVersion
    ) {
        List<StoredEvent> events = repository
                .findByStreamIdAndStreamVersionLessThanEqualOrderByStreamVersionAsc(
                        streamId,
                        inclusiveVersion
                )
                .stream()
                .map(this::toStoredEvent)
                .toList();

        long version = events.isEmpty()
                ? Account.NO_STREAM_VERSION
                : events.getLast().streamVersion();

        return new EventStream(streamId, version, events);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEvent> readStreamAfterVersion(
            String streamId,
            long exclusiveVersion
    ) {
        return repository
                .findByStreamIdAndStreamVersionGreaterThanOrderByStreamVersionAsc(
                        streamId,
                        exclusiveVersion
                )
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEvent> readAllAfterPosition(
            long exclusiveGlobalPosition
    ) {
        return repository
                .findByGlobalPositionGreaterThanOrderByGlobalPositionAsc(
                        exclusiveGlobalPosition
                )
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    @Override
    @Transactional
    public void append(
            String streamId,
            long expectedVersion,
            List<AccountEvent> newEvents
    ) {
        if (newEvents.isEmpty()) {
            return;
        }

        long actualVersion = repository
                .findFirstByStreamIdOrderByStreamVersionDesc(streamId)
                .map(StoredEventEntity::getStreamVersion)
                .orElse(Account.NO_STREAM_VERSION);

        if (actualVersion != expectedVersion) {
            throw new OptimisticConcurrencyException(
                    streamId,
                    expectedVersion,
                    actualVersion
            );
        }

        List<StoredEventEntity> entities =
                new ArrayList<>(newEvents.size());

        long nextVersion = expectedVersion + 1;

        for (AccountEvent event : newEvents) {
            entities.add(
                    new StoredEventEntity(
                            UUID.randomUUID().toString(),
                            streamId,
                            nextVersion,
                            codec.eventType(event),
                            AccountEventCodec.CURRENT_SCHEMA_VERSION,
                            codec.serialize(event),
                            Instant.now()
                    )
            );
            nextVersion++;
        }

        try {
            /*
             * UNIQUE(stream_id, stream_version) е последната atomic
             * concurrency barrier. Два writers могат едновременно да прочетат
             * същата current version; само единият ще append-не next version.
             */
            repository.saveAllAndFlush(entities);
        } catch (DataIntegrityViolationException exception) {
            throw new OptimisticConcurrencyException(
                    streamId,
                    expectedVersion,
                    exception
            );
        }
    }

    private StoredEvent toStoredEvent(
            StoredEventEntity entity
    ) {
        return new StoredEvent(
                entity.getGlobalPosition(),
                entity.getEventId(),
                entity.getStreamId(),
                entity.getStreamVersion(),
                entity.getEventType(),
                entity.getEventSchemaVersion(),
                entity.getOccurredAt(),
                codec.deserialize(
                        entity.getEventType(),
                        entity.getEventSchemaVersion(),
                        entity.getPayload()
                )
        );
    }
}
