package bg.hristomanov.education.containers.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class GreetingCache {

    private static final String KEY_PREFIX = "learning:greeting:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public GreetingCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String customerKey, String greeting) {
        redisTemplate.opsForValue().set(KEY_PREFIX + customerKey, greeting, TTL);
    }

    public Optional<String> find(String customerKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + customerKey));
    }

    public void delete(String customerKey) {
        redisTemplate.delete(KEY_PREFIX + customerKey);
    }
}
