package bg.hristomanov.education.semanticcache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class SemanticResponseCache {

    private final TextEmbeddingModel embeddingModel;
    private final double similarityThreshold;
    private final Clock clock;
    private final List<SemanticCacheEntry> entries =
            new ArrayList<>();

    public SemanticResponseCache(
            TextEmbeddingModel embeddingModel,
            double similarityThreshold,
            Clock clock
    ) {
        if (similarityThreshold < 0.0
                || similarityThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "similarityThreshold must be between 0 and 1"
            );
        }

        this.embeddingModel = embeddingModel;
        this.similarityThreshold = similarityThreshold;
        this.clock = clock;
    }

    public synchronized void put(
            String query,
            String response,
            CacheContext context,
            Duration ttl
    ) {
        removeExpired();

        entries.add(
                new SemanticCacheEntry(
                        query,
                        embeddingModel.embed(query),
                        response,
                        context.fingerprint(),
                        clock.instant().plus(ttl)
                )
        );
    }

    public synchronized Optional<SemanticCacheHit> get(
            String query,
            CacheContext context
    ) {
        removeExpired();

        double[] queryEmbedding =
                embeddingModel.embed(query);
        String contextFingerprint =
                context.fingerprint();

        SemanticCacheEntry best = null;
        double bestSimilarity = -1.0;

        for (SemanticCacheEntry entry : entries) {
            if (!entry.contextFingerprint()
                    .equals(contextFingerprint)) {
                continue;
            }

            double similarity = cosineSimilarity(
                    queryEmbedding,
                    entry.embedding()
            );

            if (similarity > bestSimilarity) {
                best = entry;
                bestSimilarity = similarity;
            }
        }

        if (best == null
                || bestSimilarity < similarityThreshold) {
            return Optional.empty();
        }

        return Optional.of(
                new SemanticCacheHit(
                        best.response(),
                        best.query(),
                        bestSimilarity
                )
        );
    }

    public synchronized int size() {
        removeExpired();
        return entries.size();
    }

    private void removeExpired() {
        Instant now = clock.instant();
        Iterator<SemanticCacheEntry> iterator =
                entries.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().expiredAt(now)) {
                iterator.remove();
            }
        }
    }

    private double cosineSimilarity(
            double[] left,
            double[] right
    ) {
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "Embedding dimensions differ"
            );
        }

        double dot = 0.0;
        double leftSquared = 0.0;
        double rightSquared = 0.0;

        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftSquared += left[index] * left[index];
            rightSquared += right[index] * right[index];
        }

        if (leftSquared == 0.0 || rightSquared == 0.0) {
            return 0.0;
        }

        return dot / (
                Math.sqrt(leftSquared)
                        * Math.sqrt(rightSquared)
        );
    }
}
