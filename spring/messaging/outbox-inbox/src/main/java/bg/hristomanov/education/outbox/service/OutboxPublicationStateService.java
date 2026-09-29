package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.outbox.OutboxEvent;
import bg.hristomanov.education.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxPublicationStateService {

    private final OutboxEventRepository outboxRepository;

    public OutboxPublicationStateService(
            OutboxEventRepository outboxRepository
    ) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String eventId) {
        OutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown outbox event: " + eventId)
                );

        event.markPublished(Instant.now());
    }
}
