package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Baseline без cache.
 *
 * <p>Този вариант не е "грешен". Той е контролна група, чрез която виждаме
 * реалната цена на всяко четене от persistent store-а.</p>
 */
@Service
public class NoCacheProductService implements CachingStrategyService {

    private final ProductRepository repository;

    public NoCacheProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public String strategyName() {
        return "no-cache";
    }

    @Override
    public Product get(long productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
    }

    @Override
    public Product update(long productId, String name, BigDecimal price) {
        return repository.save(productId, name, price);
    }

    @Override
    public void clearCache() {
        // Няма cache state за изчистване.
    }

    @Override
    public CacheStatistics cacheStatistics() {
        return CacheStatistics.empty(strategyName());
    }
}
