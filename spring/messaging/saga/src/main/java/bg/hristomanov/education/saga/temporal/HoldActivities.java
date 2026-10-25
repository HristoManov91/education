package bg.hristomanov.education.saga.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * External side effects belong to Activities, never to deterministic Workflow code.
 * Every method uses the SAME immutable business operation ID for retries and recovery.
 */
@ActivityInterface
public interface HoldActivities {

    @ActivityMethod
    void holdCredit(String operationId);

    @ActivityMethod
    void holdDebit(String operationId);

    @ActivityMethod
    void releaseCredit(String operationId);

    @ActivityMethod
    void releaseDebit(String operationId);
}
