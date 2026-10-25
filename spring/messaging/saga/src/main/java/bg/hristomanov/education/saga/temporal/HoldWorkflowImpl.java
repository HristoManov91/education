package bg.hristomanov.education.saga.temporal;

import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Temporal-orchestrated hold across two independent financial participants.
 *
 * This is a runnable teaching implementation, not an end-to-end fintech payment product.
 * Production needs durable participant data, provider-side idempotency/fencing,
 * reconciliation, bounded retries, observability and a documented terminal-state contract.
 */
public final class HoldWorkflowImpl implements HoldWorkflow {

    private final HoldActivities activities = Workflow.newActivityStub(
            HoldActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setHeartbeatTimeout(Duration.ofSeconds(3))
                    // Unlike TRY_CANCEL, do not start cleanup immediately while a
                    // remote call can still be completing (bounded by attempt timeout).
                    .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                    // Keep fault-injection tests deterministic. In production tune retry
                    // policy to downstream idempotency, budgets, and SLA.
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .build());

    @Override
    public String hold(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "operationId must not be blank", "INVALID_OPERATION_ID");
        }

        Saga saga = new Saga(new Saga.Options.Builder()
                .setParallelCompensation(false)
                .build());

        try {
            // Register BEFORE the side effect: a timeout may hide a committed hold.
            saga.addCompensation(activities::releaseCredit, operationId);
            activities.holdCredit(operationId);
            CancellationScope.throwCanceled();

            saga.addCompensation(activities::releaseDebit, operationId);
            activities.holdDebit(operationId);
            // Important: there may be no third Activity to surface cancellation.
            CancellationScope.throwCanceled();

            return "HELD";
        } catch (Exception forwardFailure) {
            // Root scope was cancelled: cleanup Activities need a detached scope.
            try {
                Workflow.newDetachedCancellationScope(saga::compensate).run();
            } catch (Exception compensationFailure) {
                // Never quietly return success after a failed compensation.
                // Caller/reconciliation must treat this as an unresolved state.
                throw ApplicationFailure.newNonRetryableFailure(
                        "Compensation required for operation " + operationId
                                + " after " + forwardFailure.getClass().getSimpleName(),
                        "COMPENSATION_REQUIRED", operationId);
            }

            // A canceled workflow must not be reported as a successful hold.
            throw forwardFailure;
        }
    }
}
