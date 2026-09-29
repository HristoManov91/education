package bg.hristomanov.education.semanticcache;

import java.time.Instant;

public record SemanticCacheEntry(
        String query,
        double[] embedding,
        String response,
        String contextFingerprint,
        Instant expiresAt
) {

    public SemanticCacheEntry {
        embedding = embedding.clone();
    }

    @Override
    public double[] embedding() {
        return embedding.clone();
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
