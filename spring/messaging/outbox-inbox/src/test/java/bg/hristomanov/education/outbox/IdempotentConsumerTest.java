package bg.hristomanov.education.outbox;

import bg.hristomanov.education.outbox.broker.BrokerMessage;
import bg.hristomanov.education.outbox.broker.InMemoryMessageBroker;
import bg.hristomanov.education.outbox.inbox.ConsumptionResult;
import bg.hristomanov.education.outbox.inbox.LoyaltyAccount;
import bg.hristomanov.education.outbox.repository.LoyaltyAccountRepository;
import bg.hristomanov.education.outbox.repository.OrderRepository;
import bg.hristomanov.education.outbox.repository.OutboxEventRepository;
import bg.hristomanov.education.outbox.repository.ProcessedMessageRepository;
import bg.hristomanov.education.outbox.service.IdempotentOrderCreatedConsumer;
import bg.hristomanov.education.outbox.service.OutboxRelayService;
import bg.hristomanov.education.outbox.service.TransactionalOutboxOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotentConsumerTest {

    private final TransactionalOutboxOrderService orderService;
    private final OutboxRelayService relayService;
    private final IdempotentOrderCreatedConsumer consumer;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final InMemoryMessageBroker broker;

    @Autowired
    IdempotentConsumerTest(
            TransactionalOutboxOrderService orderService,
            OutboxRelayService relayService,
            IdempotentOrderCreatedConsumer consumer,
            OrderRepository orderRepository,
            OutboxEventRepository outboxRepository,
            ProcessedMessageRepository processedMessageRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            InMemoryMessageBroker broker
    ) {
        this.orderService = orderService;
        this.relayService = relayService;
        this.consumer = consumer;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.broker = broker;
    }

    @BeforeEach
    void reset() {
        processedMessageRepository.deleteAll();
        loyaltyAccountRepository.deleteAll();
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        broker.clear();
    }

    @Test
    void duplicateBrokerDeliveryChangesBusinessStateOnlyOnce() {
        BrokerMessage duplicate = createDuplicateBrokerDelivery("C-DUPLICATE");

        ConsumptionResult first = consumer.consume(duplicate);
        ConsumptionResult second = consumer.consume(duplicate);

        assertThat(first).isEqualTo(ConsumptionResult.PROCESSED);
        assertThat(second).isEqualTo(ConsumptionResult.DUPLICATE);
        assertThat(processedMessageRepository.count()).isEqualTo(1);

        LoyaltyAccount account =
                loyaltyAccountRepository.findById("C-DUPLICATE").orElseThrow();

        assertThat(account.getPoints()).isEqualTo(10);
    }

    @Test
    void concurrentDuplicateDeliveriesStillApplyOneBusinessEffect() throws Exception {
        BrokerMessage message = createSingleBrokerMessage("C-CONCURRENT");
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ConsumptionResult>> futures = new ArrayList<>();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            for (int index = 0; index < callers; index++) {
                futures.add(
                        executor.submit(() -> {
                            start.await();
                            return consumer.consume(message);
                        })
                );
            }

            start.countDown();

            int processed = 0;
            int duplicates = 0;

            for (Future<ConsumptionResult> future : futures) {
                ConsumptionResult result =
                        future.get(5, TimeUnit.SECONDS);

                if (result == ConsumptionResult.PROCESSED) {
                    processed++;
                } else {
                    duplicates++;
                }
            }

            assertThat(processed).isEqualTo(1);
            assertThat(duplicates).isEqualTo(7);
            assertThat(processedMessageRepository.count()).isEqualTo(1);

            LoyaltyAccount account =
                    loyaltyAccountRepository.findById("C-CONCURRENT").orElseThrow();

            assertThat(account.getPoints()).isEqualTo(10);
        }
    }

    @Test
    void failedBusinessProcessingRollsBackInboxClaimAndEffect() {
        BrokerMessage message = createSingleBrokerMessage("C-RETRYABLE");

        assertThatThrownBy(() -> consumer.consume(message, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before local transaction commit");

        /*
         * Claim + business effect са rollback-нати заедно.
         * Broker redelivery трябва да може да обработи message-а отново.
         */
        assertThat(processedMessageRepository.count()).isZero();
        assertThat(loyaltyAccountRepository.findById("C-RETRYABLE")).isEmpty();

        ConsumptionResult retry = consumer.consume(message);

        assertThat(retry).isEqualTo(ConsumptionResult.PROCESSED);
        assertThat(processedMessageRepository.count()).isEqualTo(1);
        assertThat(
                loyaltyAccountRepository.findById("C-RETRYABLE")
                        .orElseThrow()
                        .getPoints()
        ).isEqualTo(10);
    }

    private BrokerMessage createDuplicateBrokerDelivery(String customerId) {
        orderService.createOrder(
                "ORD-" + customerId,
                customerId,
                new BigDecimal("200.00")
        );

        assertThatThrownBy(() -> relayService.publishNext(true))
                .isInstanceOf(IllegalStateException.class);

        relayService.publishNext(false);

        assertThat(broker.messages()).hasSize(2);
        assertThat(broker.messages().get(0).eventId())
                .isEqualTo(broker.messages().get(1).eventId());

        return broker.messages().getFirst();
    }

    private BrokerMessage createSingleBrokerMessage(String customerId) {
        orderService.createOrder(
                "ORD-" + customerId,
                customerId,
                new BigDecimal("200.00")
        );

        relayService.publishNext(false);

        assertThat(broker.messages()).hasSize(1);
        return broker.messages().getFirst();
    }
}
