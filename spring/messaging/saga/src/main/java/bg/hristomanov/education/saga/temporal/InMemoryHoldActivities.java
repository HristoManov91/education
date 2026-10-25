package bg.hristomanov.education.saga.temporal;

import bg.hristomanov.education.saga.cancellation.HoldingParticipant;

import java.util.Objects;

/**
 * Stand-in for HTTP adapters to two independent banking participants.
 *
 * This is purposely NOT durable: HashMap + synchronized protect only one JVM.
 * An actual adapter would call authoritative APIs with operationId and would not
 * derive "success" solely from transport-level acknowledgements.
 */
public final class InMemoryHoldActivities implements HoldActivities {

    private final HoldingParticipant credit;
    private final HoldingParticipant debit;

    public InMemoryHoldActivities(HoldingParticipant credit, HoldingParticipant debit) {
        this.credit = Objects.requireNonNull(credit, "credit");
        this.debit = Objects.requireNonNull(debit, "debit");
    }

    @Override
    public void holdCredit(String operationId) {
        credit.hold(operationId);
    }

    @Override
    public void holdDebit(String operationId) {
        debit.hold(operationId);
    }

    @Override
    public void releaseCredit(String operationId) {
        credit.compensate(operationId);
    }

    @Override
    public void releaseDebit(String operationId) {
        debit.compensate(operationId);
    }
}
