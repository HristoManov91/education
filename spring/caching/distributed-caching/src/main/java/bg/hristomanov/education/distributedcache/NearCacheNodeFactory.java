package bg.hristomanov.education.distributedcache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class NearCacheNodeFactory {

    private final ProductSource source;
    private final RedisTemplate<String, Product> redis;
    private final RedisInvalidationBus invalidationBus;

    public NearCacheNodeFactory(
            ProductSource source,
            RedisTemplate<String, Product> redis,
            RedisInvalidationBus invalidationBus
    ) {
        this.source = source;
        this.redis = redis;
        this.invalidationBus = invalidationBus;
    }

    public NearCacheNode create(String nodeId) {
        return new NearCacheNode(
                nodeId,
                source,
                redis,
                invalidationBus
        );
    }
}
