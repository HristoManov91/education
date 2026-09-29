package bg.hristomanov.education.cache.cache;

import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;
import java.util.Optional;

/**
 * Тънък wrapper около Caffeine, използван от explicit strategy примерите.
 *
 * <p>Идеята е да отделим cache store-а от caching policy (правилото кой,
 * кога и в какъв ред чете/пише). Cache-aside и write-through могат да
 * използват една и съща cache технология, но имат различен flow.</p>
 */
public class ProductCache {

    private final String name;
    private final Cache<Long, Product> cache;

    public ProductCache(String name) {
        this.name = name;
        this.cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(1))
                .recordStats()
                .build();
    }

    public Optional<Product> get(long productId) {
        return Optional.ofNullable(cache.getIfPresent(productId));
    }

    public void put(Product product) {
        cache.put(product.id(), product);
    }

    public void invalidate(long productId) {
        cache.invalidate(productId);
    }

    public void clear() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    public CacheStatistics statistics() {
        CacheStats stats = cache.stats();
        return new CacheStatistics(
                name,
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount(),
                cache.estimatedSize()
        );
    }
}
