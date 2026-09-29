package bg.hristomanov.education.patterns.structural.flyweight;

/**
 * Flyweight object: immutable intrinsic state (споделимото състояние),
 * което е еднакво за много AuditEvent instances.
 */
public record AuditEventType(
        String code,
        String severity,
        String category
) {
}
