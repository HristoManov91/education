package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;

import java.math.BigDecimal;

/**
 * Общ contract, за да сравняваме стратегиите при еднакъв business scenario.
 */
public interface CachingStrategyService {

    String strategyName();

    Product get(long productId);

    Product update(long productId, String name, BigDecimal price);

    void clearCache();

    CacheStatistics cacheStatistics();
}
