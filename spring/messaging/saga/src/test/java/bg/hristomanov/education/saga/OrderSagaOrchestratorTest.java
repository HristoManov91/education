package bg.hristomanov.education.saga;

import bg.hristomanov.education.saga.domain.ParticipantStatus;
import bg.hristomanov.education.saga.domain.SagaScenario;
import bg.hristomanov.education.saga.domain.SagaStatus;
import bg.hristomanov.education.saga.repository.InventoryReservationRepository;
import bg.hristomanov.education.saga.repository.OrderSagaRepository;
import bg.hristomanov.education.saga.repository.PaymentReservationRepository;
import bg.hristomanov.education.saga.repository.ShipmentReservationRepository;
import bg.hristomanov.education.saga.service.OrderSagaOrchestrator;
import bg.hristomanov.education.saga.service.SagaSnapshot;
import bg.hristomanov.education.saga.service.SagaTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderSagaOrchestratorTest {

    private final OrderSagaOrchestrator orchestrator;
    private final OrderSagaRepository sagaRepository;
    private final PaymentReservationRepository paymentRepository;
    private final InventoryReservationRepository inventoryRepository;
    private final ShipmentReservationRepository shipmentRepository;
    private final SagaTrace trace;

    @Autowired
    OrderSagaOrchestratorTest(
            OrderSagaOrchestrator orchestrator,
            OrderSagaRepository sagaRepository,
            PaymentReservationRepository paymentRepository,
            InventoryReservationRepository inventoryRepository,
            ShipmentReservationRepository shipmentRepository,
            SagaTrace trace
    ) {
        this.orchestrator = orchestrator;
        this.sagaRepository = sagaRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryRepository = inventoryRepository;
        this.shipmentRepository = shipmentRepository;
        this.trace = trace;
    }

    @BeforeEach
    void clean() {
        shipmentRepository.deleteAll();
        inventoryRepository.deleteAll();
        paymentRepository.deleteAll();
        sagaRepository.deleteAll();
        trace.clear();
    }

    @Test
    void happyPathCompletesAllLocalTransactions() {
        SagaSnapshot snapshot = orchestrator.start(
                "ORD-SAGA-HAPPY",
                SagaScenario.HAPPY_PATH
        );

        assertThat(snapshot.sagaStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(snapshot.paymentStatus()).isEqualTo(ParticipantStatus.RESERVED);
        assertThat(snapshot.inventoryStatus()).isEqualTo(ParticipantStatus.RESERVED);
        assertThat(snapshot.shipmentStatus()).isEqualTo(ParticipantStatus.SCHEDULED);
        assertThat(snapshot.trace()).containsExactly(
                "payment:reserve",
                "inventory:reserve",
                "shipping:schedule"
        );
    }

    @Test
    void inventoryFailureCompensatesPreviouslyCommittedPayment() {
        SagaSnapshot snapshot = orchestrator.start(
                "ORD-SAGA-INVENTORY-FAIL",
                SagaScenario.INVENTORY_FAILURE
        );

        assertThat(snapshot.sagaStatus()).isEqualTo(SagaStatus.REJECTED);
        assertThat(snapshot.paymentStatus()).isEqualTo(ParticipantStatus.RELEASED);
        assertThat(snapshot.inventoryStatus()).isNull();
        assertThat(snapshot.shipmentStatus()).isNull();
        assertThat(snapshot.trace()).containsExactly(
                "payment:reserve",
                "inventory:reserve",
                "payment:release"
        );
    }

    @Test
    void shippingFailureCompensatesInReverseOrder() {
        SagaSnapshot snapshot = orchestrator.start(
                "ORD-SAGA-SHIPPING-FAIL",
                SagaScenario.SHIPPING_FAILURE
        );

        assertThat(snapshot.sagaStatus()).isEqualTo(SagaStatus.REJECTED);
        assertThat(snapshot.paymentStatus()).isEqualTo(ParticipantStatus.RELEASED);
        assertThat(snapshot.inventoryStatus()).isEqualTo(ParticipantStatus.RELEASED);
        assertThat(snapshot.shipmentStatus()).isNull();

        assertThat(snapshot.trace()).containsExactly(
                "payment:reserve",
                "inventory:reserve",
                "shipping:schedule",
                "inventory:release",
                "payment:release"
        );
    }

    @Test
    void failedCompensationIsPersistentStateAndCanBeRetried() {
        SagaSnapshot failed = orchestrator.start(
                "ORD-SAGA-COMPENSATION-FAIL",
                SagaScenario.PAYMENT_COMPENSATION_FAILURE
        );

        assertThat(failed.sagaStatus())
                .isEqualTo(SagaStatus.COMPENSATION_REQUIRED);
        assertThat(failed.paymentStatus()).isEqualTo(ParticipantStatus.RESERVED);
        assertThat(failed.inventoryStatus()).isNull();
        assertThat(failed.failureReason()).contains("compensation");

        SagaSnapshot recovered = orchestrator.retryCompensation(failed.sagaId());

        assertThat(recovered.sagaStatus()).isEqualTo(SagaStatus.REJECTED);
        assertThat(recovered.paymentStatus()).isEqualTo(ParticipantStatus.RELEASED);
        assertThat(recovered.trace()).containsExactly("payment:release");
    }
}
