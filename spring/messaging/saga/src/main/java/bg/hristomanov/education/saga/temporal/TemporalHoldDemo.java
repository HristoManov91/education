package bg.hristomanov.education.saga.temporal;

import bg.hristomanov.education.saga.cancellation.HoldingParticipant;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * Manual demo: start a local Temporal development server first:
 *     temporal server start-dev
 *
 * No HTTP API is exposed here; the caller and worker run in one process.
 * The server must be available at 127.0.0.1:7233.
 */
public final class TemporalHoldDemo {

    private static final String TASK_QUEUE = "education-temporal-hold";

    private TemporalHoldDemo() {
    }

    public static void main(String[] args) {
        String operationId = args.length == 1 ? args[0] : "example-hold-001";

        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkerFactory factory = null;
        try {
            WorkflowClient client = WorkflowClient.newInstance(service);
            factory = WorkerFactory.newInstance(client);

            HoldingParticipant credit = new HoldingParticipant();
            HoldingParticipant debit = new HoldingParticipant();

            Worker worker = factory.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(HoldWorkflowImpl.class);
            worker.registerActivitiesImplementations(new InMemoryHoldActivities(credit, debit));
            factory.start();

            HoldWorkflow workflow = client.newWorkflowStub(
                    HoldWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId("education:hold:" + operationId)
                            .setTaskQueue(TASK_QUEUE)
                            .build());

            System.out.println("Workflow outcome: " + workflow.hold(operationId));
            System.out.println("Credit: " + credit.stateOf(operationId));
            System.out.println("Debit: " + debit.stateOf(operationId));
        } finally {
            if (factory != null) {
                factory.shutdown();
            }
            service.shutdown();
        }
    }
}
