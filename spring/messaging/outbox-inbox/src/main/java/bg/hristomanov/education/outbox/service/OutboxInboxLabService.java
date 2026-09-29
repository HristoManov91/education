package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.api.LabState;
import bg.hristomanov.education.outbox.broker.InMemoryMessageBroker;
import bg.hristomanov.education.outbox.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxInboxLabService {

    private final ProcessedMessageRepository processedMessageRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final OutboxEventRepository outboxRepository;
    private final OrderRepository orderRepository;
    private final InMemoryMessageBroker broker;

    public OutboxInboxLabService(
            ProcessedMessageRepository processedMessageRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            OutboxEventRepository outboxRepository,
            OrderRepository orderRepository,
            InMemoryMessageBroker broker
    ) {
        this.processedMessageRepository = processedMessageRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.outboxRepository = outboxRepository;
        this.orderRepository = orderRepository;
        this.broker = broker;
    }

    @Transactional
    public void reset() {
        processedMessageRepository.deleteAll();
        loyaltyAccountRepository.deleteAll();
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        broker.clear();
    }

    @Transactional(readOnly = true)
    public LabState state() {
        return new LabState(
                orderRepository.count(),
                outboxRepository.count(),
                outboxRepository.countByPublishedAtIsNull(),
                processedMessageRepository.count(),
                loyaltyAccountRepository.count(),
                broker.messages()
        );
    }
}
