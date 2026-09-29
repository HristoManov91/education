package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.broker.BrokerMessage;
import bg.hristomanov.education.outbox.broker.InMemoryMessageBroker;
import bg.hristomanov.education.outbox.outbox.OutboxEvent;
import bg.hristomanov.education.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Polling Publisher variant на Message Relay.
 *
 * <p>Publish към broker и mark-as-published НЕ могат да бъдат една atomic
 * local DB transaction. Ако publish успее, а execution спре преди mark,
 * следващият relay run ще публикува същия event отново.</p>
 *
 * <p>Това е причината Outbox да изисква idempotent consumers.</p>
 */
@Service
public class OutboxRelayService {

    private final OutboxEventRepository outboxRepository;
    private final InMemoryMessageBroker broker;
    private final OutboxPublicationStateService publicationStateService;

    public OutboxRelayService(
            OutboxEventRepository outboxRepository,
            InMemoryMessageBroker broker,
            OutboxPublicationStateService publicationStateService
    ) {
        this.outboxRepository = outboxRepository;
        this.broker = broker;
        this.publicationStateService = publicationStateService;
    }

    public Optional<String> publishNext(boolean stopAfterBrokerPublish) {
        Optional<OutboxEvent> next =
                outboxRepository.findFirstByPublishedAtIsNullOrderByCreatedAtAscIdAsc();

        if (next.isEmpty()) {
            return Optional.empty();
        }

        OutboxEvent event = next.get();

        broker.publish(
                new BrokerMessage(
                        event.getId(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getEventType(),
                        event.getPayload()
                )
        );

        if (stopAfterBrokerPublish) {
            throw new IllegalStateException(
                    "Simulated interruption after broker publish and before outbox acknowledgement"
            );
        }

        publicationStateService.markPublished(event.getId());
        return Optional.of(event.getId());
    }

    public int publishAll() {
        int published = 0;

        while (publishNext(false).isPresent()) {
            published++;
        }

        return published;
    }
}
