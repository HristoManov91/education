package bg.hristomanov.education.patterns.structural.flyweight;

import java.time.Instant;

/**
 * Context object: държи extrinsic state (уникално за конкретния event)
 * и reference към shared Flyweight.
 */
public record AuditEvent(
        String requestId,
        String userId,
        Instant occurredAt,
        String message,
        AuditEventType type
) {
}
