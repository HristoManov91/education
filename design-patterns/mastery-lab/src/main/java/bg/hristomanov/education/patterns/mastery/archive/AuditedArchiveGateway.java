package bg.hristomanov.education.patterns.mastery.archive;

import java.util.Objects;

/** Decorator adds cross-cutting behavior without modifying the archive contract. */
public final class AuditedArchiveGateway implements ArchiveGateway {
    private final ArchiveGateway delegate;
    private final AuditTrail audit;

    public AuditedArchiveGateway(ArchiveGateway delegate, AuditTrail audit) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override public String archive(String reference, String content) {
        try {
            String archivedId = delegate.archive(reference, content);
            audit.add("ARCHIVE_OK:" + reference);
            return archivedId;
        } catch (RuntimeException ex) {
            audit.add("ARCHIVE_FAILED:" + reference);
            throw ex; // never convert failure into a successful receipt
        }
    }
}
