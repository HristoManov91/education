package bg.hristomanov.education.saga.service;

import bg.hristomanov.education.saga.domain.OrderSaga;
import bg.hristomanov.education.saga.domain.SagaScenario;
import org.springframework.stereotype.Service;

/**
 * Orchestration-based Saga.
 *
 * <p>Няма една distributed ACID transaction. Всеки participant commit-ва
 * собствена local transaction. При по-късен failure orchestrator-ът изпълнява
 * semantic compensating actions в обратен ред.</p>
 */
@Service
public class OrderSagaOrchestrator {

    private final SagaStateService sagaState;
    private final PaymentParticipantService payment;
    private final InventoryParticipantService inventory;
    private final ShippingParticipantService shipping;
    private final SagaTrace trace;

    public OrderSagaOrchestrator(
            SagaStateService sagaState,
            PaymentParticipantService payment,
            InventoryParticipantService inventory,
            ShippingParticipantService shipping,
            SagaTrace trace
    ) {
        this.sagaState = sagaState;
        this.payment = payment;
        this.inventory = inventory;
        this.shipping = shipping;
        this.trace = trace;
    }

    public SagaSnapshot start(
            String orderReference,
            SagaScenario scenario
    ) {
        trace.clear();

        Long sagaId = sagaState.create(orderReference);

        try {
            payment.reserve(
                    sagaId,
                    scenario == SagaScenario.PAYMENT_FAILURE
            );
            inventory.reserve(
                    sagaId,
                    scenario == SagaScenario.INVENTORY_FAILURE
                            || scenario == SagaScenario.PAYMENT_COMPENSATION_FAILURE
            );
            shipping.schedule(
                    sagaId,
                    scenario == SagaScenario.SHIPPING_FAILURE
            );

            sagaState.complete(sagaId);
        } catch (SagaStepException stepFailure) {
            compensate(
                    sagaId,
                    stepFailure.getMessage(),
                    scenario == SagaScenario.PAYMENT_COMPENSATION_FAILURE
            );
        }

        return snapshot(sagaId);
    }

    public SagaSnapshot retryCompensation(Long sagaId) {
        trace.clear();

        try {
            shipping.cancel(sagaId);
            inventory.release(sagaId);
            payment.release(sagaId, false);
            sagaState.reject(sagaId);
        } catch (SagaCompensationException compensationFailure) {
            sagaState.requireCompensationRetry(
                    sagaId,
                    compensationFailure.getMessage()
            );
        }

        return snapshot(sagaId);
    }

    private void compensate(
            Long sagaId,
            String originalFailure,
            boolean failPaymentCompensation
    ) {
        sagaState.startCompensation(sagaId, originalFailure);

        try {
            /*
             * Reverse order is intentional:
             *
             * forward: payment -> inventory -> shipping
             * compensate: shipping -> inventory -> payment
             */
            shipping.cancel(sagaId);
            inventory.release(sagaId);
            payment.release(sagaId, failPaymentCompensation);
            sagaState.reject(sagaId);
        } catch (SagaCompensationException compensationFailure) {
            sagaState.requireCompensationRetry(
                    sagaId,
                    originalFailure
                            + " | compensation: "
                            + compensationFailure.getMessage()
            );
        }
    }

    public SagaSnapshot snapshot(Long sagaId) {
        OrderSaga saga = sagaState.get(sagaId);

        return new SagaSnapshot(
                saga.getId(),
                saga.getStatus(),
                payment.status(sagaId),
                inventory.status(sagaId),
                shipping.status(sagaId),
                saga.getFailureReason(),
                trace.entries()
        );
    }
}
