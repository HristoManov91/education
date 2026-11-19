package bg.hristomanov.education.patterns.mastery.archive;

/** Duplicate write is a business conflict, not an idempotent replay. */
public final class ArchiveConflictException extends RuntimeException {
    public ArchiveConflictException(String reference) {
        super("archive reference already exists: " + reference);
    }
}
