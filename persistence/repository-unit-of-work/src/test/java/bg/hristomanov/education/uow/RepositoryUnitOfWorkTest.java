package bg.hristomanov.education.uow;

import bg.hristomanov.education.uow.domain.OrderStatus;
import bg.hristomanov.education.uow.domain.PurchaseOrder;
import bg.hristomanov.education.uow.service.OrderApplicationService;
import bg.hristomanov.education.uow.service.PersistenceContextProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RepositoryUnitOfWorkTest {

    private final OrderApplicationService orderService;
    private final PersistenceContextProbeService probeService;

    @Autowired
    RepositoryUnitOfWorkTest(
            OrderApplicationService orderService,
            PersistenceContextProbeService probeService
    ) {
        this.orderService = orderService;
        this.probeService = probeService;
    }

    @Test
    void managedEntityChangeIsPersistedWithoutExplicitSaveCall() {
        long orderId = orderService.createOrder(
                "ORD-DIRTY-CHECKING",
                new BigDecimal("120.00")
        );

        PurchaseOrder before = orderService.getOrder(orderId);
        assertThat(before.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(before.getVersion()).isZero();

        /*
         * markPaid() НЕ извиква repository.save(order).
         * Промяната се открива от persistence context dirty checking.
         */
        orderService.markPaid(orderId);

        PurchaseOrder after = orderService.getOrder(orderId);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(after.getVersion()).isEqualTo(1);
    }

    @Test
    void persistenceContextActsAsIdentityMapWithinOneTransaction() {
        long orderId = orderService.createOrder(
                "ORD-IDENTITY-MAP",
                new BigDecimal("80.00")
        );

        assertThat(probeService.twoLoadsReturnSameManagedInstance(orderId)).isTrue();
    }

    @Test
    void flushDoesNotMeanCommitAndRollbackStillWins() {
        long orderId = orderService.createOrder(
                "ORD-FLUSH-ROLLBACK",
                new BigDecimal("200.00")
        );

        assertThatThrownBy(() -> probeService.markPaidFlushThenFail(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated failure");

        PurchaseOrder reloaded = orderService.getOrder(orderId);

        /*
         * SQL UPDATE е бил flush-нат преди exception-а, но transaction-ът
         * е rollback-нат. DB state остава NEW.
         */
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(reloaded.getVersion()).isZero();
    }

    @Test
    void detachedEntityChangeIsNotDirtyChecked() {
        long orderId = orderService.createOrder(
                "ORD-DETACHED",
                new BigDecimal("55.00")
        );

        probeService.detachThenChange(orderId);

        PurchaseOrder reloaded = orderService.getOrder(orderId);

        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(reloaded.getVersion()).isZero();
    }
}
