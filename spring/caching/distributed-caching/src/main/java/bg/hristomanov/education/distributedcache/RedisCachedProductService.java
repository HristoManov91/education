package bg.hristomanov.education.distributedcache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class RedisCachedProductService {

    private final ProductSource source;

    public RedisCachedProductService(ProductSource source) {
        this.source = source;
    }

    @Cacheable(
            cacheNames = "products",
            key = "#productId"
    )
    public Product get(long productId) {
        return source.findRequired(productId);
    }

    @CacheEvict(
            cacheNames = "products",
            key = "#productId"
    )
    public void evict(long productId) {
        // The annotation is the operation.
    }
}
