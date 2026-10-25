package bg.hristomanov.education.saga.temporal;

import bg.hristomanov.education.saga.cancellation.HoldingParticipant;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Temporal Workflow + Activity Worker + in-memory Temporal service.
 * No external Temporal deployment is necessary.
 *
 * The controlled Actors/latches run in Activities, NEVER in Workflow code.
 * This tests Temporal orchestration semantics, not real HTTP/provider durability.
 */
class TemporalHoldWorkflowIntegrationTest {

    private static final String TASK_QUEUE = "temporal-hold-ci";

    private TestWorkflowEnvironment environment;
    private Worker worker;
    private WorkflowClient client;
    private ControlledActivities activities;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(HoldWorkflowImpl.class);
        client = environment.getWorkflowClient();
        activities = new ControlledActivities();
    }

    @AfterEach
    void tearDown() {
        if (activities != null) {
            activities.releaseAll(); // No worker should be stuck on a CountDownLatch.
        }
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void happyPathCommitsBothHoldsViaTemporalWorkflowAndActivities() {
        startWorker();

        HoldWorkflow workflow = newWorkflow("op-happy");
        assertThat(workflow.hold("op-happy")).isEqualTo("HELD");

        assertThat(activities.credit.stateOf("op-happy"))
                .isEqualTo(HoldingParticipant.State.HELD);
        assertThat(activities.debit.stateOf("op-happy"))
                .isEqualTo(HoldingParticipant.State.HELD);
    }

    @Test
    void lostCreditAcknowledgementStillRunsRegisteredCompensation() {
        activities.failCreditAfterCommit = true;
        startWorker();

        HoldWorkflow workflow = newWorkflow("op-credit-response-lost");
        assertThatThrownBy(() -> workflow.hold("op-credit-response-lost"))
                .isInstanceOf(WorkflowFailedException.class);

        assertThat(activities.credit.stateOf("op-credit-response-lost"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
        assertThat(activities.debit.stateOf("op-credit-response-lost"))
                .isEqualTo(HoldingParticipant.State.ABSENT);
    }

    @Test
    void lostDebitAcknowledgementCompensatesInReverseOrder() {
        activities.failDebitAfterCommit = true;
        startWorker();

        HoldWorkflow workflow = newWorkflow("op-debit-response-lost");
        assertThatThrownBy(() -> workflow.hold("op-debit-response-lost"))
                .isInstanceOf(WorkflowFailedException.class);

        assertThat(activities.credit.stateOf("op-debit-response-lost"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
        assertThat(activities.debit.stateOf("op-debit-response-lost"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
    }

    @Test
    void cancelDuringFinalActivityRunsDetachedCompensations() throws InterruptedException {
        activities.blockDebit = true;
        startWorker();

        HoldWorkflow workflow = newWorkflow("op-cancel-last");
        WorkflowClient.start(workflow::hold, "op-cancel-last");

        try {
            assertThat(activities.enteredDebit.await(15, TimeUnit.SECONDS)).isTrue();
            // Cancellation is recorded before the blocked Activity is allowed to finish.
            WorkflowStub.fromTyped(workflow).cancel();
        } finally {
            activities.proceedDebit.countDown();
        }

        assertThatThrownBy(() -> result(workflow)).isInstanceOf(RuntimeException.class);
        assertThat(activities.credit.stateOf("op-cancel-last"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
        assertThat(activities.debit.stateOf("op-cancel-last"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
    }

    @Test
    void cancelDuringFirstActivityCannotLeaveAnOrphanedHold() throws InterruptedException {
        activities.blockCredit = true;
        startWorker();

        HoldWorkflow workflow = newWorkflow("op-cancel-first");
        WorkflowClient.start(workflow::hold, "op-cancel-first");

        try {
            assertThat(activities.enteredCredit.await(15, TimeUnit.SECONDS)).isTrue();
            WorkflowStub.fromTyped(workflow).cancel();
        } finally {
            activities.proceedCredit.countDown();
        }

        assertThatThrownBy(() -> result(workflow)).isInstanceOf(RuntimeException.class);
        assertThat(activities.credit.stateOf("op-cancel-first"))
                .isIn(HoldingParticipant.State.CANCEL_REQUESTED,
                        HoldingParticipant.State.RELEASED);
        assertThat(activities.debit.stateOf("op-cancel-first"))
                .isEqualTo(HoldingParticipant.State.ABSENT);
    }

    @Test
    void callerWaitTimeoutDoesNotAutomaticallyCancelTheWorkflow() throws InterruptedException {
        activities.blockDebit = true;
        startWorker();

        HoldWorkflow workflow = newWorkflow("op-http-deadline");
        WorkflowClient.start(workflow::hold, "op-http-deadline");

        try {
            assertThat(activities.enteredDebit.await(15, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> WorkflowStub.fromTyped(workflow)
                    .getResult(100, TimeUnit.MILLISECONDS, String.class))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            activities.proceedDebit.countDown();
        }

        assertThat(result(workflow)).isEqualTo("HELD");
        assertThat(activities.credit.stateOf("op-http-deadline"))
                .isEqualTo(HoldingParticipant.State.HELD);
        assertThat(activities.debit.stateOf("op-http-deadline"))
                .isEqualTo(HoldingParticipant.State.HELD);
    }

    @Test
    void duplicateWorkflowIdWhileRunningCannotCreateASecondExecution()
            throws InterruptedException {
        activities.blockDebit = true;
        startWorker();

        HoldWorkflow first = newWorkflow("op-duplicate");
        WorkflowClient.start(first::hold, "op-duplicate");

        try {
            assertThat(activities.enteredDebit.await(15, TimeUnit.SECONDS)).isTrue();
            HoldWorkflow duplicate = newWorkflow("op-duplicate");

            assertThatThrownBy(() -> WorkflowClient.start(duplicate::hold, "op-duplicate"))
                    .isInstanceOf(WorkflowExecutionAlreadyStarted.class);
        } finally {
            activities.proceedDebit.countDown();
        }

        assertThat(result(first)).isEqualTo("HELD");
        assertThat(activities.credit.stateOf("op-duplicate"))
                .isEqualTo(HoldingParticipant.State.HELD);
        assertThat(activities.debit.stateOf("op-duplicate"))
                .isEqualTo(HoldingParticipant.State.HELD);
    }

    @Test
    void failedCompensationIsSurfacedAsUnresolvedBusinessState() {
        activities.failDebitAfterCommit = true;
        activities.failCreditCompensation = true;
        startWorker();

        HoldWorkflow workflow = newWorkflow("op-repair-required");

        assertThatThrownBy(() -> workflow.hold("op-repair-required"))
                .isInstanceOf(WorkflowFailedException.class)
                .hasMessageContaining("COMPENSATION_REQUIRED");

        // A successful compensation of debit does not repair an unsuccessful
        // compensation of credit. There is no dishonest "CANCELLED" claim.
        assertThat(activities.credit.stateOf("op-repair-required"))
                .isEqualTo(HoldingParticipant.State.HELD);
        assertThat(activities.debit.stateOf("op-repair-required"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
    }

    @Test
    void gatewayTimeoutAndClientRetryAttachToOneRunningWorkflow() throws InterruptedException {
        activities.blockDebit = true;
        startWorker();

        HoldGateway gateway = new HoldGateway(client, TASK_QUEUE);
        String operationId = "op-gateway-retry";

        try {
            HoldGateway.Submission first =
                    gateway.startOrAttach(operationId, java.time.Duration.ofMillis(100));
            assertThat(first.outcome()).isEqualTo(HoldGateway.Outcome.PENDING);
            assertThat(first.workflowId()).isEqualTo(HoldGateway.workflowIdFor(operationId));

            assertThat(activities.enteredDebit.await(15, TimeUnit.SECONDS)).isTrue();

            HoldGateway.Submission retry =
                    gateway.startOrAttach(operationId, java.time.Duration.ofMillis(100));
            assertThat(retry.outcome()).isEqualTo(HoldGateway.Outcome.PENDING);
            assertThat(retry.workflowId()).isEqualTo(first.workflowId());
        } finally {
            activities.proceedDebit.countDown();
        }

        HoldGateway.Submission completed =
                gateway.startOrAttach(operationId, java.time.Duration.ofSeconds(15));

        assertThat(completed.outcome()).isEqualTo(HoldGateway.Outcome.HELD);
        assertThat(activities.credit.stateOf(operationId))
                .isEqualTo(HoldingParticipant.State.HELD);
        assertThat(activities.debit.stateOf(operationId))
                .isEqualTo(HoldingParticipant.State.HELD);
    }

    private void startWorker() {
        worker.registerActivitiesImplementations(activities);
        environment.start();
    }

    private HoldWorkflow newWorkflow(String operationId) {
        return client.newWorkflowStub(
                HoldWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("education:hold:" + operationId)
                        .setTaskQueue(TASK_QUEUE)
                        .build());
    }

    private static String result(HoldWorkflow workflow) {
        try {
            return WorkflowStub.fromTyped(workflow).getResult(
                    20, TimeUnit.SECONDS, String.class);
        } catch (TimeoutException timeout) {
            throw new AssertionError("Workflow did not reach a terminal status", timeout);
        }
    }

    private static final class ControlledActivities implements HoldActivities {

        private final HoldingParticipant credit = new HoldingParticipant();
        private final HoldingParticipant debit = new HoldingParticipant();

        private final CountDownLatch enteredCredit = new CountDownLatch(1);
        private final CountDownLatch enteredDebit = new CountDownLatch(1);
        private final CountDownLatch proceedCredit = new CountDownLatch(1);
        private final CountDownLatch proceedDebit = new CountDownLatch(1);

        private volatile boolean blockCredit;
        private volatile boolean blockDebit;
        private volatile boolean failCreditAfterCommit;
        private volatile boolean failDebitAfterCommit;
        private volatile boolean failCreditCompensation;

        @Override
        public void holdCredit(String operationId) {
            if (blockCredit) {
                enteredCredit.countDown();
                await(proceedCredit);
            }
            credit.hold(operationId);
            if (failCreditAfterCommit) {
                throw new IllegalStateException("HTTP reply lost after credit commit");
            }
        }

        @Override
        public void holdDebit(String operationId) {
            if (blockDebit) {
                enteredDebit.countDown();
                await(proceedDebit);
            }
            debit.hold(operationId);
            if (failDebitAfterCommit) {
                throw new IllegalStateException("HTTP reply lost after debit commit");
            }
        }

        @Override
        public void releaseCredit(String operationId) {
            if (failCreditCompensation) {
                throw new IllegalStateException("Credit service unavailable during compensation");
            }
            credit.compensate(operationId);
        }

        @Override
        public void releaseDebit(String operationId) {
            debit.compensate(operationId);
        }

        private void releaseAll() {
            proceedCredit.countDown();
            proceedDebit.countDown();
        }

        private static void await(CountDownLatch gate) {
            try {
                if (!gate.await(12, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test gate timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test gate interrupted", e);
            }
        }
    }
}
