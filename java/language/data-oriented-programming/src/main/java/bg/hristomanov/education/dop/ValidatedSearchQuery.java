package bg.hristomanov.education.dop;

import java.util.Objects;

/** An immutable search request that validates inputs before its superclass is constructed (JDK 25). */
public final class ValidatedSearchQuery extends SearchQueryBase {
    private final int limit;

    public ValidatedSearchQuery(String rawText, int limit) {
        String normalized = Objects.requireNonNull(rawText, "rawText").strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Search text must not be blank");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Result limit must be between 1 and 100");
        }
        // The prologue above uses arguments/local state, never the not-yet-initialized 'this'.
        super(normalized);
        this.limit = limit;
    }

    public int limit() { return limit; }
}

/** A minimal parent showing exactly when normalized text becomes owned by the instance. */
abstract class SearchQueryBase {
    private final String text;

    protected SearchQueryBase(String normalizedText) {
        this.text = Objects.requireNonNull(normalizedText, "normalizedText");
    }

    public final String text() { return text; }
}
