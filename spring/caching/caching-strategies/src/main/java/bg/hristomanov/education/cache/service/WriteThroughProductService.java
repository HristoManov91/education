package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.cache.ProductCache;
import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Application-managed write-through пример.
 *
 * <p>Write request-ът се счита за завършен едва след като source of truth
 * и cache са синхронно обновени. Това дава по-силен immediate-read mental model,
 * но write latency включва и двете операции.</p>
 */
@Service
public class WriteThroughProductService implements CachingStrategyService {

    private final ProductRepository repository;
    private final ProductCache cache = new ProductCache("write-through");

    public WriteThroughProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public String strategyName() {
        return "write-through";
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
         * ACK към caller-а идва след cache update-а. Ако тази стъпка се провали
         * след успешен DB write, вече имаме partial failure и трябва да имаме
         * retry/invalidation/reconciliation стратегия в реална система.
         */
        cache.put(persisted);
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
