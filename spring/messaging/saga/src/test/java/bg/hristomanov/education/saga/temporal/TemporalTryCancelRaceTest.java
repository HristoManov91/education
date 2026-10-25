package bg.hristomanov.education.saga.temporal;

import bg.hristomanov.education.saga.cancellation.HoldingParticipant;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real Temporal TRY_CANCEL race, not an artificial scheduling-only simulation.
 *
 * Here the Activity stays in flight WITHOUT heartbeat and is deliberately released
 * only AFTER the Workflow has executed its detached compensation.
 * The "bad" participant does not remember a cancel for an unknown hold, so its
 * late Activity can re-create the reservation. The "good" participant fences it.
 */
class TemporalTryCancelRaceTest {

    private TestWorkflowEnvironment environment;
    private Worker worker;
    private WorkflowClient client;
    private RaceActivities activities;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        worker = environment.newWorker("try-cancel-race");
        worker.registerWorkflowImplementationTypes(UnsafeTryCancelHoldWorkflowImpl.class);
        client = environment.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        if (activities != null) {
            activities.deliverLateDebit.countDown();
        }
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void tryCancelPlusNaiveReleaseCanLeaveAnOrphanedDebitHold() throws InterruptedException {
        runRace(false);

        // The forward Activity was already executing when the Workflow canceled,
        // and it committed AFTER both compensation Activities had finished.
        assertThat(activities.creditStillHeld()).isFalse();
        assertThat(activities.debitStillHeld()).isTrue(); // documented production BUG
    }

    @Test
    void participantCancellationMarkerRejectsSameLateHoldEvenWithTryCancel()
            throws InterruptedException {
        runRace(true);

        assertThat(activities.creditStillHeld()).isFalse();
        assertThat(activities.debitStillHeld()).isFalse();
        assertThat(activities.debit.stateOf("race-operation"))
                .isEqualTo(HoldingParticipant.State.CANCEL_REQUESTED);
    }

    private void runRace(boolean useFencedParticipant) throws InterruptedException {
        activities = new RaceActivities(useFencedParticipant);
        worker.registerActivitiesImplementations(activities);
        environment.start();

        HoldWorkflow workflow = client.newWorkflowStub(
                HoldWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("race-" + useFencedParticipant)
                        .setTaskQueue("try-cancel-race")
                        .build());

        WorkflowClient.start(workflow::hold, "race-operation");
        assertThat(activities.debitEntered.await(15, TimeUnit.SECONDS)).isTrue();

        // Temporal sends cancellation to Workflow; TRY_CANCEL allows it to
        // compensate without waiting for this in-flight Activity to finish.
        WorkflowStub.fromTyped(workflow).cancel();

        assertThat(activities.debitCompensated.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(activities.creditCompensated.await(15, TimeUnit.SECONDS)).isTrue();

        // It is crucial that the compensations have run BEFORE the debit hold commits.
        assertThat(activities.debitStillHeld()).isFalse();
        activities.deliverLateDebit.countDown();
        assertThat(activities.debitApplied.await(15, TimeUnit.SECONDS)).isTrue();
    }

    private static final class RaceActivities implements HoldActivities {

        private final boolean fenced;
        private final HoldingParticipant credit = new HoldingParticipant();
        private final HoldingParticipant debit = new HoldingParticipant();
        private final Set<String> naiveDebitHolds = ConcurrentHashMap.newKeySet();

        private final CountDownLatch debitEntered = new CountDownLatch(1);
        private final CountDownLatch debitCompensated = new CountDownLatch(1);
        private final CountDownLatch creditCompensated = new CountDownLatch(1);
        private final CountDownLatch deliverLateDebit = new CountDownLatch(1);
        private final CountDownLatch debitApplied = new CountDownLatch(1);

        private RaceActivities(boolean fenced) {
            this.fenced = fenced;
        }

        @Override
        public void holdCredit(String operationId) {
            credit.hold(operationId);
        }

        @Override
        public void holdDebit(String operationId) {
            debitEntered.countDown();
            try {
                if (!deliverLateDebit.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Late HTTP hold gate timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("HTTP hold interrupted", interrupted);
            }

            // Executed AFTER Workflow's released/compensated state, proving
            // cancellation of Workflow != cancellation of external side effect.
            if (fenced) {
                debit.hold(operationId);
            } else {
                naiveDebitHolds.add(operationId);
            }
            debitApplied.countDown();
        }

        @Override
        public void releaseCredit(String operationId) {
            credit.compensate(operationId);
            creditCompensated.countDown();
        }

        @Override
        public void releaseDebit(String operationId) {
            if (fenced) {
                debit.compensate(operationId);
            } else {
                naiveDebitHolds.remove(operationId);
            }
            debitCompensated.countDown();
        }

        private boolean creditStillHeld() {
            return credit.stateOf("race-operation") == HoldingParticipant.State.HELD;
        }

        private boolean debitStillHeld() {
            return fenced
                    ? debit.stateOf("race-operation") == HoldingParticipant.State.HELD
                    : naiveDebitHolds.contains("race-operation");
        }
    }
}
