package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.cache.ProductCache;
import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refresh-ahead стратегия за "hot" keys (често четени ключове).
 *
 * <p>Вместо да чакаме cache miss и тогава request-ът да плати latency-то
 * на repository load-а, background refresh периодично обновява вече
 * използваните ключове.</p>
 */
@Service
public class RefreshAheadProductService implements CachingStrategyService {

    private final ProductRepository repository;
    private final ProductCache cache = new ProductCache("refresh-ahead");
    private final Set<Long> hotProductIds = ConcurrentHashMap.newKeySet();

    public RefreshAheadProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public String strategyName() {
        return "refresh-ahead";
    }

    @Override
    public Product get(long productId) {
        hotProductIds.add(productId);

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
        cache.put(persisted);
        hotProductIds.add(productId);
        return persisted;
    }

    /**
     * Refresh-ва само ключове, които вече са били използвани.
     *
     * <p>Това е умишлено различно от Caffeine {@code refreshAfterWrite}:
     * там refresh-ът е access-triggered след refresh interval-а. Тук показваме
     * по-буквалния proactive refresh-ahead flow чрез scheduler.</p>
     */
    public int refreshHotEntries() {
        int refreshed = 0;

        for (Long productId : hotProductIds) {
            Optional<Product> latest = repository.findById(productId);
            if (latest.isPresent()) {
                cache.put(latest.get());
                refreshed++;
            } else {
                cache.invalidate(productId);
            }
        }

        return refreshed;
    }

    @Scheduled(fixedDelayString = "${cache-lab.refresh-ahead.refresh-ms:5000}")
    public void scheduledRefresh() {
        refreshHotEntries();
    }

    @Override
    public void clearCache() {
        cache.clear();
        hotProductIds.clear();
    }

    @Override
    public CacheStatistics cacheStatistics() {
        return cache.statistics();
    }
}
