package bg.hristomanov.education.consistency;

import org.junit.jupiter.api.Test;
import static bg.hristomanov.education.consistency.HybridOrderWorkflow.*;
import static org.junit.jupiter.api.Assertions.*;

class HybridOrderWorkflowTest {
    @Test void happyPathPublishesOneAuthoritativeTerminalFact() {
        var workflow = new HybridOrderWorkflow("o");
        workflow.stockReserved();
        assertTrue(workflow.terminalFact().isEmpty());
        workflow.paymentApproved();
        var fact = workflow.terminalFact().orElseThrow();
        workflow.paymentApproved();
        assertSame(fact,workflow.terminalFact().orElseThrow());
        assertEquals(State.CONFIRMED,fact.state());
    }
    @Test void knownRejectionCancelsOnlyAfterReleaseAcknowledged() {
        var workflow = new HybridOrderWorkflow("o");
        workflow.stockReserved();
        workflow.paymentRejected();
        workflow.paymentRejected();
        assertEquals(State.RELEASING_STOCK,workflow.state());
        assertTrue(workflow.terminalFact().isEmpty());
        workflow.stockReleased();
        var fact = workflow.terminalFact().orElseThrow();
        workflow.stockReleased();
        assertSame(fact,workflow.terminalFact().orElseThrow());
        assertEquals(State.CANCELLED,fact.state());
    }
    @Test void unknownPaymentDoesNotAuthorizeBlindCompensation() {
        var workflow = new HybridOrderWorkflow("o");
        workflow.stockReserved(); workflow.paymentTimeout(); workflow.paymentTimeout();
        assertEquals(State.PAYMENT_UNKNOWN,workflow.state());
        assertThrows(IllegalStateException.class,workflow::stockReleased);
        assertTrue(workflow.terminalFact().isEmpty());
        workflow.paymentApproved(); // authoritative reconciliation
        assertEquals(State.CONFIRMED,workflow.state());
    }
    @Test void reconciliationCanAlsoEstablishKnownRejection() {
        var workflow = new HybridOrderWorkflow("o");
        workflow.stockReserved(); workflow.paymentTimeout();
        workflow.paymentRejected(); workflow.stockReleased();
        assertEquals(State.CANCELLED,workflow.state());
    }
    @Test void optionalSubscriberFailureDoesNotCancelConfirmedOrderAndRetryIsIdempotent() {
        var workflow = new HybridOrderWorkflow("o");
        workflow.stockReserved(); workflow.paymentApproved();
        var subscriber = new NotificationProjection();
        var event = workflow.terminalFact().orElseThrow();
        assertFalse(subscriber.onConfirmed(event,false));
        assertEquals(State.CONFIRMED,workflow.state());
        assertEquals(0,subscriber.deliveredCount());
        assertTrue(subscriber.onConfirmed(event,true));
        assertTrue(subscriber.onConfirmed(event,false)); // duplicate after successful delivery
        assertEquals(1,subscriber.deliveredCount());
        assertEquals(State.CONFIRMED,workflow.state());
    }
    @Test void unavailableStockDoesNotStartPaymentAndConflictingOutcomeIsRejected() {
        var workflow = new HybridOrderWorkflow("o");
        workflow.stockUnavailable();
        assertEquals(State.CANCELLED,workflow.state());
        assertThrows(IllegalStateException.class,workflow::paymentApproved);
        assertFalse(new NotificationProjection().onConfirmed(workflow.terminalFact().orElseThrow(),true));
    }
}
