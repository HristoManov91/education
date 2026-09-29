package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.config.CacheConfiguration;
import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Същият read/write проблем, но през Spring Cache abstraction.
 *
 * <p>Важно: annotations се прилагат чрез Spring proxy. Self-invocation
 * (метод от този bean извиква друг cache-annotated метод на същия bean)
 * не минава през proxy-то и е типичен framework pitfall.</p>
 */
@Service
public class SpringCacheProductService implements CachingStrategyService {

    private final ProductRepository repository;
    private final CacheManager cacheManager;

    public SpringCacheProductService(ProductRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    @Override
    public String strategyName() {
        return "spring-cache";
    }

    /**
     * sync=true позволява на provider-а да синхронизира конкурентни loads
     * за един и същ key, така че burst от едновременни misses да не удари
     * source of truth N пъти.
     */
    @Override
    @Cacheable(
            cacheNames = CacheConfiguration.SPRING_PRODUCTS_CACHE,
            key = "#productId",
            sync = true
    )
    public Product get(long productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
    }

    @Override
    @CachePut(
            cacheNames = CacheConfiguration.SPRING_PRODUCTS_CACHE,
            key = "#productId"
    )
    public Product update(long productId, String name, BigDecimal price) {
        return repository.save(productId, name, price);
    }

    @Override
    @CacheEvict(
            cacheNames = CacheConfiguration.SPRING_PRODUCTS_CACHE,
            allEntries = true
    )
    public void clearCache() {
        // Eviction се извършва от interceptor-а след успешно приключване на метода.
    }

    @Override
    public CacheStatistics cacheStatistics() {
        org.springframework.cache.Cache springCache =
                cacheManager.getCache(CacheConfiguration.SPRING_PRODUCTS_CACHE);

        if (!(springCache instanceof CaffeineCache caffeineCache)) {
            return CacheStatistics.empty(strategyName());
        }

        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        CacheStats stats = nativeCache.stats();

        return new CacheStatistics(
                strategyName(),
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount(),
                nativeCache.estimatedSize()
        );
    }
}
