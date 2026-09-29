package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.cache.ProductCache;
import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Реалистичен bad пример: read path-ът е кеширан правилно, но write path-ът
 * забравя invalidation (инвалидиране/премахване на вече остарялата стойност).
 *
 * <p>Точно затова cache invalidation е correctness проблем, а не само
 * performance tuning.</p>
 */
@Service
public class NaiveCacheAsideProductService implements CachingStrategyService {

    private final ProductRepository repository;
    private final ProductCache cache = new ProductCache("naive-cache-aside");

    public NaiveCacheAsideProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public String strategyName() {
        return "naive-cache-aside";
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
         * BUG: cache-ът не се invalidира и не се обновява.
         * Следващият read може да върне стара цена/име до TTL expiry.
         */
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
