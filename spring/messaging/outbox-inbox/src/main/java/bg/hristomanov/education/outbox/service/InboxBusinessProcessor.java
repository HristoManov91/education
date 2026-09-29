package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.broker.BrokerMessage;
import bg.hristomanov.education.outbox.event.OrderCreatedPayload;
import bg.hristomanov.education.outbox.inbox.LoyaltyAccount;
import bg.hristomanov.education.outbox.inbox.ProcessedMessage;
import bg.hristomanov.education.outbox.repository.LoyaltyAccountRepository;
import bg.hristomanov.education.outbox.repository.ProcessedMessageRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumer-side atomic boundary.
 *
 * <p>Processed-message claim и business side effect трябва да commit-нат
 * заедно. Ако business processing fail-не, claim-ът също се rollback-ва,
 * за да може broker redelivery да опита отново.</p>
 */
@Service
public class InboxBusinessProcessor {

    public static final String CONSUMER_NAME = "loyalty-order-created";
    public static final int ORDER_CREATED_POINTS = 10;

    private final ProcessedMessageRepository processedMessageRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final JsonMapper jsonMapper;

    public InboxBusinessProcessor(
            ProcessedMessageRepository processedMessageRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            JsonMapper jsonMapper
    ) {
        this.processedMessageRepository = processedMessageRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processNew(
            BrokerMessage message,
            boolean failBeforeCommit
    ) {
        /*
         * UNIQUE(consumer_name, event_id) е atomic duplicate guard.
         *
         * saveAndFlush() кара constraint-а да се провери преди business
         * side effect-а. При concurrent duplicate само един consumer call
         * печели claim-а.
         */
        processedMessageRepository.saveAndFlush(
                new ProcessedMessage(
                        CONSUMER_NAME,
                        message.eventId(),
                        Instant.now()
                )
        );

        OrderCreatedPayload payload = deserialize(message.payload());

        LoyaltyAccount account = loyaltyAccountRepository
                .findById(payload.customerId())
                .orElseGet(() -> new LoyaltyAccount(payload.customerId()));

        account.addPoints(ORDER_CREATED_POINTS);
        loyaltyAccountRepository.save(account);

        if (failBeforeCommit) {
            throw new IllegalStateException(
                    "Simulated consumer failure before local transaction commit"
            );
        }
    }

    private OrderCreatedPayload deserialize(String payload) {
        try {
            return jsonMapper.readValue(payload, OrderCreatedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Cannot deserialize OrderCreated payload",
                    exception
            );
        }
    }
}
