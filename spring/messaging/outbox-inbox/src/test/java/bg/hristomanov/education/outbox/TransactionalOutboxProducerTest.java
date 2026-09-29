package bg.hristomanov.education.outbox;

import bg.hristomanov.education.outbox.broker.BrokerMessage;
import bg.hristomanov.education.outbox.broker.InMemoryMessageBroker;
import bg.hristomanov.education.outbox.repository.OrderRepository;
import bg.hristomanov.education.outbox.repository.OutboxEventRepository;
import bg.hristomanov.education.outbox.service.NaiveOrderService;
import bg.hristomanov.education.outbox.service.OutboxRelayService;
import bg.hristomanov.education.outbox.service.TransactionalOutboxOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransactionalOutboxProducerTest {

    private final NaiveOrderService naiveOrderService;
    private final TransactionalOutboxOrderService outboxOrderService;
    private final OutboxRelayService relayService;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final InMemoryMessageBroker broker;

    @Autowired
    TransactionalOutboxProducerTest(
            NaiveOrderService naiveOrderService,
            TransactionalOutboxOrderService outboxOrderService,
            OutboxRelayService relayService,
            OrderRepository orderRepository,
            OutboxEventRepository outboxRepository,
            InMemoryMessageBroker broker
    ) {
        this.naiveOrderService = naiveOrderService;
        this.outboxOrderService = outboxOrderService;
        this.relayService = relayService;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.broker = broker;
    }

    @BeforeEach
    void reset() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        broker.clear();
    }

    @Test
    void naiveCommitThenPublishCanLoseTheEvent() {
        assertThatThrownBy(() ->
                naiveOrderService.createOrder(
                        "ORD-NAIVE-LOST",
                        "C-1",
                        new BigDecimal("100.00"),
                        true
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after DB commit");

        /*
         * Business state е commit-нат, но message няма.
         * Няма DB rollback, който вече да поправи тази inconsistency.
         */
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isZero();
        assertThat(broker.messages()).isEmpty();
    }

    @Test
    void orderAndOutboxEventCommitTogetherOrRollbackTogether() {
        assertThatThrownBy(() ->
                outboxOrderService.createOrderAndFailBeforeCommit(
                        "ORD-ATOMIC-ROLLBACK",
                        "C-2",
                        new BigDecimal("120.00")
                )
        )
                .isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();

        outboxOrderService.createOrder(
                "ORD-ATOMIC-COMMIT",
                "C-2",
                new BigDecimal("120.00")
        );

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1);

        /*
         * Business transaction не публикува директно към broker.
         */
        assertThat(broker.messages()).isEmpty();
    }

    @Test
    void relayCanPublishTheSameOutboxEventMoreThanOnce() {
        outboxOrderService.createOrder(
                "ORD-RELAY-DUPLICATE",
                "C-3",
                new BigDecimal("150.00")
        );

        String eventId = outboxRepository.findAll().getFirst().getId();

        assertThatThrownBy(() -> relayService.publishNext(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after broker publish");

        assertThat(broker.messages()).hasSize(1);
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1);

        /*
         * Relay рестартира: редът още изглежда unpublished и се изпраща пак.
         */
        relayService.publishNext(false);

        List<BrokerMessage> messages = broker.messages();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).eventId()).isEqualTo(eventId);
        assertThat(messages.get(1).eventId()).isEqualTo(eventId);
        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }
}
