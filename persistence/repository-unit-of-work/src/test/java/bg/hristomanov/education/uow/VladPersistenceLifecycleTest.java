package bg.hristomanov.education.uow;

import bg.hristomanov.education.uow.domain.OrderStatus;
import bg.hristomanov.education.uow.service.OrderApplicationService;
import bg.hristomanov.education.uow.service.PersistenceContextProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VladPersistenceLifecycleTest {
    @Autowired
    private OrderApplicationService orders;

    @Autowired
    private PersistenceContextProbeService probes;

    @Test
    void mergeReturnsManagedCopyAndPersistsDetachedChanges() {
        long id = orders.createOrder("VM08-MERGE-" + UUID.randomUUID(), new BigDecimal("100"));
        assertThat(probes.mergeDetachedChangeAndCheckIdentity(id)).isTrue();
        assertThat(orders.getOrder(id).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(orders.getOrder(id).getVersion()).isEqualTo(1);
    }

    @Test
    void autoFlushBeforeOverlappingJpqlQuerySeesDirtyCheckedStatus() {
        long id = orders.createOrder("VM09-AUTO-" + UUID.randomUUID(), new BigDecimal("100"));
        assertThat(probes.autoFlushBeforeOverlappingScalarQuery(id)).isTrue();
        assertThat(orders.getOrder(id).getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
