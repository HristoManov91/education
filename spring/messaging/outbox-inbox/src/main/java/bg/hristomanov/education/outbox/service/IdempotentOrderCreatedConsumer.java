package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.broker.BrokerMessage;
import bg.hristomanov.education.outbox.inbox.ConsumptionResult;
import bg.hristomanov.education.outbox.repository.ProcessedMessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Inbox / Idempotent Consumer orchestration.
 *
 * <p>Първият exists() check е fast path за обикновена redelivery, но не е
 * concurrency guarantee. При два едновременни duplicate messages correctness
 * идва от database UNIQUE constraint-а в ProcessedMessage.</p>
 */
@Service
public class IdempotentOrderCreatedConsumer {

    private final ProcessedMessageRepository processedMessageRepository;
    private final InboxBusinessProcessor businessProcessor;

    public IdempotentOrderCreatedConsumer(
            ProcessedMessageRepository processedMessageRepository,
            InboxBusinessProcessor businessProcessor
    ) {
        this.processedMessageRepository = processedMessageRepository;
        this.businessProcessor = businessProcessor;
    }

    public ConsumptionResult consume(BrokerMessage message) {
        return consume(message, false);
    }

    public ConsumptionResult consume(
            BrokerMessage message,
            boolean failBeforeCommit
    ) {
        if (processedMessageRepository.existsByConsumerNameAndEventId(
                InboxBusinessProcessor.CONSUMER_NAME,
                message.eventId()
        )) {
            return ConsumptionResult.DUPLICATE;
        }

        try {
            businessProcessor.processNew(message, failBeforeCommit);
            return ConsumptionResult.PROCESSED;
        } catch (DataIntegrityViolationException exception) {
            /*
             * Concurrent duplicate: друга transaction е спечелила
             * UNIQUE(consumer_name, event_id) claim-а.
             */
            return ConsumptionResult.DUPLICATE;
        }
    }
}
