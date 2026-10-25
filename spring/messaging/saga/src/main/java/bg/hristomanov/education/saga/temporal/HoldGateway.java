package bg.hristomanov.education.saga.temporal;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Boundary between a synchronous caller and a durable Temporal Workflow.
 *
 * Returning PENDING means ONLY that this caller did not observe a terminal
 * outcome within its budget. It is not equivalent to FAILED or CANCELLED.
 *
 * Production callers must additionally validate tenant + request fingerprint,
 * bound ID retention/reuse, and map WorkflowFailedException into a documented
 * error / recovery state. Do not create a new operation ID on an HTTP retry.
 */
public final class HoldGateway {

    public enum Outcome {
        HELD, PENDING
    }

    public record Submission(String workflowId, Outcome outcome) {
    }

    private final WorkflowClient client;
    private final String taskQueue;

    public HoldGateway(WorkflowClient client, String taskQueue) {
        this.client = Objects.requireNonNull(client, "client");
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue");
        if (taskQueue.isBlank()) {
            throw new IllegalArgumentException("taskQueue must not be blank");
        }
    }

    public Submission startOrAttach(String operationId, Duration waitBudget) {
        validate(operationId);
        Objects.requireNonNull(waitBudget, "waitBudget");
        if (waitBudget.toMillis() < 1) {
            throw new IllegalArgumentException("waitBudget must be at least 1 millisecond");
        }

        String workflowId = workflowIdFor(operationId);
        HoldWorkflow workflow = client.newWorkflowStub(
                HoldWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(taskQueue)
                        // Prevent a new execution reusing a COMPLETED workflow ID
                        // while Temporal retention keeps the completed execution.
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                        .build());

        try {
            WorkflowClient.start(workflow::hold, operationId);
        } catch (WorkflowExecutionAlreadyStarted alreadyRunningOrCompleted) {
            // Attach to the same logical operation instead of starting a second hold.
        }

        WorkflowStub existingExecution = client.newUntypedWorkflowStub(workflowId);
        try {
            String result = existingExecution.getResult(
                    waitBudget.toMillis(), TimeUnit.MILLISECONDS, String.class);
            if (!"HELD".equals(result)) {
                throw new IllegalStateException("Unexpected workflow result: " + result);
            }
            return new Submission(workflowId, Outcome.HELD);
        } catch (TimeoutException notObservedYet) {
            return new Submission(workflowId, Outcome.PENDING);
        }
    }

    /**
     * A cancellation REQUEST is not proof that participant funds are released.
     * The caller must still use status/reconciliation to verify business outcome.
     */
    public void requestCancellation(String operationId) {
        validate(operationId);
        client.newUntypedWorkflowStub(workflowIdFor(operationId)).cancel();
    }

    public static String workflowIdFor(String operationId) {
        validate(operationId);
        return "education:hold:" + operationId;
    }

    private static void validate(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must not be blank");
        }
    }
}
