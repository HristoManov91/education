package bg.hristomanov.education.distributedcache;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

@Component
public class RedisInvalidationBus
        implements MessageListener {

    public static final String CHANNEL =
            "edu:product-cache:invalidate";

    private final StringRedisTemplate redis;
    private final Map<String, LongConsumer> listeners =
            new ConcurrentHashMap<>();

    public RedisInvalidationBus(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void register(
            String nodeId,
            LongConsumer listener
    ) {
        listeners.put(nodeId, listener);
    }

    public void unregister(String nodeId) {
        listeners.remove(nodeId);
    }

    public void publish(
            String originNodeId,
            long productId
    ) {
        redis.convertAndSend(
                CHANNEL,
                originNodeId + "|" + productId
        );
    }

    @Override
    public void onMessage(
            Message message,
            byte[] pattern
    ) {
        String payload = new String(
                message.getBody(),
                StandardCharsets.UTF_8
        );

        String[] parts = payload.split("\\|", 2);
        long productId = Long.parseLong(parts[1]);

        /*
         * Invalidating origin too is harmless and keeps the rule simple:
         * every local L1 copy is disposable after a write.
         */
        listeners.values()
                .forEach(listener -> listener.accept(productId));
    }
}
