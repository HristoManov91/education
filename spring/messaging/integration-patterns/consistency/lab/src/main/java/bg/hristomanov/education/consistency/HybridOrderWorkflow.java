package bg.hristomanov.education.consistency;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** In-memory transition model; durable saga/participant transactions are covered in the existing Saga lab. */
public final class HybridOrderWorkflow {
    public enum State { WAITING_STOCK, WAITING_PAYMENT, PAYMENT_UNKNOWN, RELEASING_STOCK, CONFIRMED, CANCELLED }
    public record TerminalFact(String orderId, State state) {
        public TerminalFact {
            Objects.requireNonNull(orderId);
            if (state != State.CONFIRMED && state != State.CANCELLED) throw new IllegalArgumentException("Not terminal");
        }
    }
    private final String orderId;
    private State state = State.WAITING_STOCK;
    private TerminalFact terminalFact;

    public HybridOrderWorkflow(String orderId) {
        this.orderId = Objects.requireNonNull(orderId);
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId required");
    }
    public State state() { return state; }
    public Optional<TerminalFact> terminalFact() { return Optional.ofNullable(terminalFact); }
    public void stockReserved() { require(State.WAITING_STOCK); state = State.WAITING_PAYMENT; }
    public void stockUnavailable() { require(State.WAITING_STOCK); finish(State.CANCELLED); }
    public void paymentTimeout() {
        if (state == State.PAYMENT_UNKNOWN) return;
        require(State.WAITING_PAYMENT);
        state = State.PAYMENT_UNKNOWN;
    }
    public void paymentApproved() {
        if (state == State.CONFIRMED) return;
        requirePaymentResolution();
        finish(State.CONFIRMED);
    }
    public void paymentRejected() {
        if (state == State.RELEASING_STOCK) return;
        requirePaymentResolution();
        state = State.RELEASING_STOCK;
    }
    public void stockReleased() {
        if (state == State.CANCELLED) return;
        require(State.RELEASING_STOCK);
        finish(State.CANCELLED);
    }
    private void requirePaymentResolution() {
        if (state != State.WAITING_PAYMENT && state != State.PAYMENT_UNKNOWN)
            throw new IllegalStateException("Payment outcome incompatible with " + state);
    }
    private void require(State expected) {
        if (state != expected) throw new IllegalStateException("Expected " + expected + ", got " + state);
    }
    private void finish(State next) { state = next; terminalFact = new TerminalFact(orderId, next); }

    /** Optional subscriber has no reference to the coordinator or participant internals. */
    public static final class NotificationProjection {
        private final Set<String> sentFor = new LinkedHashSet<>();
        public boolean onConfirmed(TerminalFact event, boolean dependencyAvailable) {
            if (event.state() != State.CONFIRMED) return false;
            if (sentFor.contains(event.orderId())) return true;
            if (!dependencyAvailable) return false;
            sentFor.add(event.orderId());
            return true;
        }
        public int deliveredCount() { return sentFor.size(); }
    }
}
