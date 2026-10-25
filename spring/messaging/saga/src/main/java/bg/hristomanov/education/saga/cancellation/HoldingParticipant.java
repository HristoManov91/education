package bg.hristomanov.education.saga.cancellation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory model of one authoritative participant's operation state.
 *
 * The cancellation marker makes a late, duplicate hold for the same operation ID a no-op.
 * This is an educational model, NOT a durable multi-instance implementation.
 * A real participant must enforce equivalent transitions atomically in its own database,
 * and must coordinate them with the real external business side effect.
 */
public final class HoldingParticipant {

    public enum State {
        ABSENT,
        HELD,
        CANCEL_REQUESTED,
        RELEASED
    }

    private final Map<String, State> states = new HashMap<>();

    public synchronized State hold(String operationId) {
        validate(operationId);

        State before = states.getOrDefault(operationId, State.ABSENT);
        State after = before == State.ABSENT ? State.HELD : before;
        states.put(operationId, after);
        return after;
    }

    public synchronized State compensate(String operationId) {
        validate(operationId);

        State before = states.getOrDefault(operationId, State.ABSENT);
        State after = switch (before) {
            case ABSENT -> State.CANCEL_REQUESTED;
            case HELD -> State.RELEASED;
            case CANCEL_REQUESTED, RELEASED -> before;
        };
        states.put(operationId, after);
        return after;
    }

    public synchronized State stateOf(String operationId) {
        validate(operationId);
        return states.getOrDefault(operationId, State.ABSENT);
    }

    private static void validate(String operationId) {
        Objects.requireNonNull(operationId, "operationId");
        if (operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must not be blank");
        }
    }
}
