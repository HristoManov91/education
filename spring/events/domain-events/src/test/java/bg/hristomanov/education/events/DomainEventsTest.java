package bg.hristomanov.education.events;

import bg.hristomanov.education.events.domain.OrderAggregate;
import bg.hristomanov.education.events.domain.OrderStatus;
import bg.hristomanov.education.events.listener.EventObservationProbe;
import bg.hristomanov.education.events.repository.OrderAggregateRepository;
import bg.hristomanov.education.events.repository.OrderPaidProjectionRepository;
import bg.hristomanov.education.events.service.OrderDomainEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DomainEventsTest {

    private final OrderDomainEventService orderService;
    private final OrderAggregateRepository orderRepository;
    private final OrderPaidProjectionRepository projectionRepository;
    private final EventObservationProbe probe;

    @Autowired
    DomainEventsTest(
            OrderDomainEventService orderService,
            OrderAggregateRepository orderRepository,
            OrderPaidProjectionRepository projectionRepository,
            EventObservationProbe probe
    ) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.projectionRepository = projectionRepository;
        this.probe = probe;
    }

    @BeforeEach
    void clean() {
        projectionRepository.deleteAll();
        orderRepository.deleteAll();
        probe.reset();
    }

    @Test
    void repositorySavePublishesDomainEventAndAfterCommitReaction() {
        long orderId = orderService.createOrder("ORD-EVENT-SAVE");

        orderService.markPaidAndSave(orderId);

        OrderAggregate reloaded = orderService.get(orderId);

        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(probe.synchronousEvents()).hasSize(1);
        assertThat(probe.afterCommitEvents()).hasSize(1);
        assertThat(probe.afterRollbackEvents()).isEmpty();
        assertThat(probe.integrationEvents()).hasSize(1);
        assertThat(probe.integrationEvents().getFirst().schemaVersion())
                .isEqualTo(1);
        assertThat(projectionRepository.count()).isEqualTo(1);
    }

    @Test
    void dirtyCheckingWithoutRepositorySavePersistsStateButDoesNotPublishSpringDataDomainEvent() {
        long orderId = orderService.createOrder("ORD-EVENT-NO-SAVE");

        orderService.markPaidWithoutSave(orderId);

        OrderAggregate reloaded = orderService.get(orderId);

        /*
         * Това е връзката с Unit of Work lab-а:
         * JPA dirty checking работи независимо от repository.save().
         */
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);

        /*
         * Но Spring Data @DomainEvents hook е вързан към repository methods.
         */
        assertThat(probe.synchronousEvents()).isEmpty();
        assertThat(probe.afterCommitEvents()).isEmpty();
        assertThat(projectionRepository.count()).isZero();
    }

    @Test
    void synchronousListenerFailureRollsBackBusinessTransactionAndTriggersAfterRollback() {
        long orderId = orderService.createOrder("ORD-EVENT-ROLLBACK");
        probe.failSynchronousListener(true);

        assertThatThrownBy(() -> orderService.markPaidAndSave(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synchronous domain event listener");

        probe.failSynchronousListener(false);

        OrderAggregate reloaded = orderService.get(orderId);

        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(probe.synchronousEvents()).hasSize(1);
        assertThat(probe.afterCommitEvents()).isEmpty();
        assertThat(probe.afterRollbackEvents()).hasSize(1);
        assertThat(projectionRepository.count()).isZero();
    }
}
