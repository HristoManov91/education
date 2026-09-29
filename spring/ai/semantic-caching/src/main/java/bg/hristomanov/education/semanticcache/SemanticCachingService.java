package bg.hristomanov.education.semanticcache;

import java.time.Duration;
import java.util.Optional;

public class SemanticCachingService {

    private final SemanticResponseCache cache;
    private final CountingAiModel aiModel;
    private final Duration ttl;

    public SemanticCachingService(
            SemanticResponseCache cache,
            CountingAiModel aiModel,
            Duration ttl
    ) {
        this.cache = cache;
        this.aiModel = aiModel;
        this.ttl = ttl;
    }

    public SemanticAnswer ask(
            String query,
            CacheContext context
    ) {
        Optional<SemanticCacheHit> cached =
                cache.get(query, context);

        if (cached.isPresent()) {
            SemanticCacheHit hit = cached.get();

            return new SemanticAnswer(
                    hit.response(),
                    true,
                    hit.matchedQuery(),
                    hit.similarity()
            );
        }

        String generated =
                aiModel.generate(query);

        cache.put(
                query,
                generated,
                context,
                ttl
        );

        return new SemanticAnswer(
                generated,
                false,
                query,
                1.0
        );
    }

    public record SemanticAnswer(
            String response,
            boolean cacheHit,
            String matchedQuery,
            double similarity
    ) {
    }
}
