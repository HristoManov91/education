package bg.hristomanov.education.patterns.structural.proxy;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxy има същия interface като real subject-а и контролира достъпа до него.
 *
 * <p>Тук proxy-то добавя caching. Други proxy-та могат да добавят security,
 * lazy loading, remote access, transactions или metrics.</p>
 */
public class CachingProductCatalogProxy implements ProductCatalog {

    private final ProductCatalog delegate;
    private final ConcurrentHashMap<String, BigDecimal> cache = new ConcurrentHashMap<>();

    public CachingProductCatalogProxy(ProductCatalog delegate) {
        this.delegate = delegate;
    }

    @Override
    public BigDecimal priceFor(String sku) {
        return cache.computeIfAbsent(sku, delegate::priceFor);
    }
}
