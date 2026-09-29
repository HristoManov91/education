package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.cache.ProductCache;
import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Cache-aside (lazy loading) стратегия.
 *
 * <p>Application кодът притежава read flow-а: първо cache, при miss -
 * repository, после populate на cache-а. При write първо записваме source of truth,
 * след което invalidираме стария cache entry.</p>
 */
@Service
public class CacheAsideProductService implements CachingStrategyService {

    private final ProductRepository repository;
    private final ProductCache cache = new ProductCache("cache-aside");

    public CacheAsideProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public String strategyName() {
        return "cache-aside";
    }

    @Override
    public Product get(long productId) {
        Optional<Product> cached = cache.get(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Product loaded = repository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
        cache.put(loaded);
        return loaded;
    }

    @Override
    public Product update(long productId, String name, BigDecimal price) {
        Product persisted = repository.save(productId, name, price);

        /*
         * Избираме invalidate вместо cache.put(), защото при cache-aside
         * следващият read отново трябва да мине през source of truth.
         * Това избягва част от dual-write риска, макар да има малък race window.
         */
        cache.invalidate(productId);
        return persisted;
    }

    @Override
    public void clearCache() {
        cache.clear();
    }

    @Override
    public CacheStatistics cacheStatistics() {
        return cache.statistics();
    }
}
