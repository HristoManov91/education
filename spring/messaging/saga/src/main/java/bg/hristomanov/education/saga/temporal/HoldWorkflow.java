package bg.hristomanov.education.saga.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * A single business operation is identified by a stable operation ID.
 * The Temporal Workflow ID is separately supplied by the client.
 */
@WorkflowInterface
public interface HoldWorkflow {

    @WorkflowMethod
    String hold(String operationId);
}
