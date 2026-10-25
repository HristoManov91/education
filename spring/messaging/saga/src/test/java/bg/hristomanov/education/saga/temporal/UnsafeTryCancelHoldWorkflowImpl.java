package bg.hristomanov.education.saga.temporal;

import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * INTENTIONALLY UNSAFE teaching example: TRY_CANCEL can release a participant
 * while its remote Activity's irreversible business side effect is still in flight.
 * Keep this in src/test only. Compare with HoldWorkflowImpl (WAIT) and participant
 * fencing in TemporalTryCancelRaceTest.
 */
public final class UnsafeTryCancelHoldWorkflowImpl implements HoldWorkflow {

    private final HoldActivities activities = Workflow.newActivityStub(
            HoldActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setCancellationType(ActivityCancellationType.TRY_CANCEL)
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .build());

    @Override
    public String hold(String operationId) {
        Saga saga = new Saga(new Saga.Options.Builder()
                .setParallelCompensation(false).build());
        try {
            saga.addCompensation(activities::releaseCredit, operationId);
            activities.holdCredit(operationId);

            saga.addCompensation(activities::releaseDebit, operationId);
            activities.holdDebit(operationId);
            return "HELD";
        } catch (Exception failure) {
            Workflow.newDetachedCancellationScope(saga::compensate).run();
            throw failure;
        }
    }
}
