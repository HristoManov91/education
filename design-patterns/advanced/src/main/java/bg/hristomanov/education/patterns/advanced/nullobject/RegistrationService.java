package bg.hristomanov.education.patterns.advanced.nullobject;

import java.util.Objects;

/** The service depends on a real contract, not nullable conditional branches. */
public final class RegistrationService {
    private final AuditSink audit;

    public RegistrationService(AuditSink audit) {
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    public String register(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference is required");
        }
        // The demonstration does not persist a user or promise durable audit.
        audit.record(new AuditSink.AuditEntry("REGISTER", reference));
        return "registered:" + reference;
    }
}
