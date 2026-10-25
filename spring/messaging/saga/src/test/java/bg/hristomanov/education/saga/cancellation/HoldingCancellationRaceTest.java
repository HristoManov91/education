package bg.hristomanov.education.saga.cancellation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingCancellationRaceTest {

    /**
     * The forward HTTP request has already left the orchestrator, but has not
     * reached this participant yet. A naive "release if present" does nothing.
     */
    @Test
    void naiveCompensationBeforeDelayedHoldLeavesFundsReserved() {
        NaiveParticipant participant = new NaiveParticipant();

        participant.compensate("hold-001");
        participant.hold("hold-001");

        // This assertion DOCUMENTS the bug; it is not the desired business outcome.
        assertThat(participant.isHeld("hold-001")).isTrue();
    }

    @Test
    void cancellationMarkerPreventsTheLateHoldFromRecreatingAReservation() {
        HoldingParticipant participant = new HoldingParticipant();

        assertThat(participant.compensate("hold-001"))
                .isEqualTo(HoldingParticipant.State.CANCEL_REQUESTED);
        assertThat(participant.hold("hold-001"))
                .isEqualTo(HoldingParticipant.State.CANCEL_REQUESTED);
        assertThat(participant.stateOf("hold-001"))
                .isNotEqualTo(HoldingParticipant.State.HELD);
    }

    @Test
    void responseLossAfterCommittedHoldStillRequiresCompensation() {
        HoldingParticipant participant = new HoldingParticipant();

        assertThatThrownBy(() -> {
            participant.hold("hold-002"); // A side effect happened at the participant.
            throw new IllegalStateException("HTTP response lost");
        }).isInstanceOf(IllegalStateException.class);

        assertThat(participant.stateOf("hold-002"))
                .isEqualTo(HoldingParticipant.State.HELD);

        assertThat(participant.compensate("hold-002"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
    }

    @Test
    void duplicateForwardAndCompensationAttemptsHaveOneLogicalEffect() {
        HoldingParticipant participant = new HoldingParticipant();

        assertThat(participant.hold("hold-003"))
                .isEqualTo(HoldingParticipant.State.HELD);
        assertThat(participant.hold("hold-003"))
                .isEqualTo(HoldingParticipant.State.HELD);
        assertThat(participant.compensate("hold-003"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
        assertThat(participant.compensate("hold-003"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
        assertThat(participant.hold("hold-003"))
                .isEqualTo(HoldingParticipant.State.RELEASED);
    }

    @Test
    void twoParticipantCancellationHandlesAnAlreadyHeldAndANotYetAppliedStep() {
        HoldingParticipant credit = new HoldingParticipant();
        HoldingParticipant debit = new HoldingParticipant();
        String operationId = "hold-004";

        credit.hold(operationId);
        // Debit hold is in flight; no acknowledgement has arrived.
        credit.compensate(operationId);
        debit.compensate(operationId);
        // Simulate a delayed debit request reaching its participant after cleanup.
        debit.hold(operationId);

        assertThat(credit.stateOf(operationId))
                .isEqualTo(HoldingParticipant.State.RELEASED);
        assertThat(debit.stateOf(operationId))
                .isEqualTo(HoldingParticipant.State.CANCEL_REQUESTED);
    }

    @Test
    void reconciliationRepairsAnOrphanWhenCancellationIntentIsAuthoritative() {
        HoldingParticipant participant = new HoldingParticipant();
        String operationId = "hold-005";

        participant.hold(operationId);
        // Simulate a lost reply / lost orchestration step: persisted business intent
        // in the source system is CANCEL, while the participant still reports HELD.
        boolean businessIntentCancelled = true;

        if (businessIntentCancelled &&
                participant.stateOf(operationId) == HoldingParticipant.State.HELD) {
            participant.compensate(operationId);
        }

        assertThat(participant.stateOf(operationId))
                .isEqualTo(HoldingParticipant.State.RELEASED);
        assertThat(participant.compensate(operationId))
                .isEqualTo(HoldingParticipant.State.RELEASED);
    }

    @Test
    void operationIdentityMustNotBeBlank() {
        HoldingParticipant participant = new HoldingParticipant();

        assertThatThrownBy(() -> participant.hold(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> participant.compensate(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static final class NaiveParticipant {
        private final Set<String> holds = new HashSet<>();

        void hold(String operationId) {
            holds.add(operationId);
        }

        void compensate(String operationId) {
            holds.remove(operationId);
        }

        boolean isHeld(String operationId) {
            return holds.contains(operationId);
        }
    }
}
