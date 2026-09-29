package bg.hristomanov.education.semanticcache;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticCachingTest {

    private static final CacheContext DEFAULT_CONTEXT =
            new CacheContext(
                    "tenant-A",
                    "system-v1",
                    "model-v1",
                    "kb-v1",
                    "en"
            );

    @Test
    void exactCacheMissesParaphraseButSemanticCacheHitsIt() {
        String original =
                "What is the capital of France?";
        String paraphrase =
                "Tell me France's capital city";

        ExactResponseCache exact =
                new ExactResponseCache();
        exact.put(
                original,
                DEFAULT_CONTEXT,
                "Paris"
        );

        assertThat(
                exact.get(
                        paraphrase,
                        DEFAULT_CONTEXT
                )
        ).isEmpty();

        MutableClock clock =
                new MutableClock(
                        Instant.parse("2026-09-29T12:00:00Z")
                );

        SemanticResponseCache semantic =
                new SemanticResponseCache(
                        new KeywordEmbeddingModel(),
                        0.95,
                        clock
                );

        semantic.put(
                original,
                "Paris",
                DEFAULT_CONTEXT,
                Duration.ofHours(1)
        );

        Optional<SemanticCacheHit> hit =
                semantic.get(
                        paraphrase,
                        DEFAULT_CONTEXT
                );

        assertThat(hit).isPresent();
        assertThat(hit.orElseThrow().response())
                .isEqualTo("Paris");
        assertThat(hit.orElseThrow().similarity())
                .isGreaterThan(0.95);
    }

    @Test
    void contextFingerprintPreventsCrossTenantOrConfigurationHits() {
        SemanticResponseCache cache =
                new SemanticResponseCache(
                        new KeywordEmbeddingModel(),
                        0.90,
                        Clock.systemUTC()
                );

        cache.put(
                "What is the capital of France?",
                "Paris",
                DEFAULT_CONTEXT,
                Duration.ofHours(1)
        );

        CacheContext otherTenant =
                new CacheContext(
                        "tenant-B",
                        "system-v1",
                        "model-v1",
                        "kb-v1",
                        "en"
                );

        CacheContext newerKnowledgeBase =
                new CacheContext(
                        "tenant-A",
                        "system-v1",
                        "model-v1",
                        "kb-v2",
                        "en"
                );

        assertThat(
                cache.get(
                        "Tell me France's capital city",
                        otherTenant
                )
        ).isEmpty();

        assertThat(
                cache.get(
                        "Tell me France's capital city",
                        newerKnowledgeBase
                )
        ).isEmpty();
    }

    @Test
    void lenientThresholdCanCreateFalseSemanticHit() {
        String currentBalance =
                "What is my account balance?";
        String historicalBalance =
                "What was my account balance yesterday?";

        SemanticResponseCache lenient =
                new SemanticResponseCache(
                        new KeywordEmbeddingModel(),
                        0.90,
                        Clock.systemUTC()
                );

        lenient.put(
                currentBalance,
                "Current balance is 100 EUR",
                DEFAULT_CONTEXT,
                Duration.ofHours(1)
        );

        SemanticCacheHit falseHit =
                lenient.get(
                                historicalBalance,
                                DEFAULT_CONTEXT
                        )
                        .orElseThrow();

        /*
         * Embedding similarity is high, but business meaning differs.
         * Semantic similarity does not imply answer equivalence.
         */
        assertThat(falseHit.similarity())
                .isGreaterThan(0.90);
        assertThat(falseHit.response())
                .isEqualTo("Current balance is 100 EUR");

        SemanticResponseCache strict =
                new SemanticResponseCache(
                        new KeywordEmbeddingModel(),
                        0.95,
                        Clock.systemUTC()
                );

        strict.put(
                currentBalance,
                "Current balance is 100 EUR",
                DEFAULT_CONTEXT,
                Duration.ofHours(1)
        );

        assertThat(
                strict.get(
                        historicalBalance,
                        DEFAULT_CONTEXT
                )
        ).isEmpty();
    }

    @Test
    void ttlRemovesSemanticEntry() {
        MutableClock clock =
                new MutableClock(
                        Instant.parse("2026-09-29T12:00:00Z")
                );

        SemanticResponseCache cache =
                new SemanticResponseCache(
                        new KeywordEmbeddingModel(),
                        0.90,
                        clock
                );

        cache.put(
                "What is the weather today?",
                "Sunny",
                DEFAULT_CONTEXT,
                Duration.ofMinutes(5)
        );

        assertThat(
                cache.get(
                        "Tell me today's weather",
                        DEFAULT_CONTEXT
                )
        ).isPresent();

        clock.advance(Duration.ofMinutes(6));

        assertThat(
                cache.get(
                        "Tell me today's weather",
                        DEFAULT_CONTEXT
                )
        ).isEmpty();

        assertThat(cache.size()).isZero();
    }

    @Test
    void semanticHitAvoidsSecondExpensiveModelCall() {
        CountingAiModel model =
                new CountingAiModel();

        SemanticResponseCache cache =
                new SemanticResponseCache(
                        new KeywordEmbeddingModel(),
                        0.95,
                        Clock.systemUTC()
                );

        SemanticCachingService service =
                new SemanticCachingService(
                        cache,
                        model,
                        Duration.ofHours(1)
                );

        SemanticCachingService.SemanticAnswer first =
                service.ask(
                        "What is the capital of France?",
                        DEFAULT_CONTEXT
                );

        SemanticCachingService.SemanticAnswer second =
                service.ask(
                        "Tell me France's capital city",
                        DEFAULT_CONTEXT
                );

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.response())
                .isEqualTo(first.response());
        assertThat(model.callCount()).isEqualTo(1);
    }

    private static final class MutableClock
            extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
