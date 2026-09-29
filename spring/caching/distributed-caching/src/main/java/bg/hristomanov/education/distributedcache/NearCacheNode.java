package bg.hristomanov.education.distributedcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;

public final class NearCacheNode implements AutoCloseable {

    private static final Duration L2_TTL =
            Duration.ofMinutes(10);

    private final String nodeId;
    private final ProductSource source;
    private final RedisTemplate<String, Product> redis;
    private final RedisInvalidationBus invalidationBus;
    private final Cache<Long, Product> l1 =
            Caffeine.newBuilder()
                    .maximumSize(100)
                    .build();

    public NearCacheNode(
            String nodeId,
            ProductSource source,
            RedisTemplate<String, Product> redis,
            RedisInvalidationBus invalidationBus
    ) {
        this.nodeId = nodeId;
        this.source = source;
        this.redis = redis;
        this.invalidationBus = invalidationBus;

        invalidationBus.register(
                nodeId,
                this::invalidateLocal
        );
    }

    public Product get(long productId) {
        Product local = l1.getIfPresent(productId);
        if (local != null) {
            return local;
        }

        String key = l2Key(productId);
        Product shared =
                redis.opsForValue().get(key);

        if (shared != null) {
            l1.put(productId, shared);
            return shared;
        }

        Product loaded =
                source.findRequired(productId);

        redis.opsForValue().set(
                key,
                loaded,
                L2_TTL
        );
        l1.put(productId, loaded);

        return loaded;
    }

    public Product updatePrice(
            long productId,
            BigDecimal newPrice
    ) {
        Product updated =
                source.updatePrice(
                        productId,
                        newPrice
                );

        redis.opsForValue().set(
                l2Key(productId),
                updated,
                L2_TTL
        );

        invalidationBus.publish(
                nodeId,
                productId
        );

        return updated;
    }

    public boolean localContains(long productId) {
        return l1.getIfPresent(productId) != null;
    }

    public void invalidateLocal(long productId) {
        l1.invalidate(productId);
    }

    public String nodeId() {
        return nodeId;
    }

    @Override
    public void close() {
        invalidationBus.unregister(nodeId);
        l1.invalidateAll();
    }

    private String l2Key(long productId) {
        return "edu:l2:product:" + productId;
    }
}
