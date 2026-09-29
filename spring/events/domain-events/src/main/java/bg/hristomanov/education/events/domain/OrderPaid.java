package bg.hristomanov.education.events.domain;

import java.time.Instant;

/**
 * Domain Event: business fact expressed in domain language.
 *
 * <p>Името е в минало време: не "PayOrderCommand", а "OrderPaid" —
 * нещо вече се е случило в aggregate-а.</p>
 */
public record OrderPaid(
        long orderId,
        String orderReference,
        Instant occurredAt
) {
}
