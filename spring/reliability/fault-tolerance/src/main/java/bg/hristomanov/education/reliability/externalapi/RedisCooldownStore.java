package bg.hristomanov.education.reliability.externalapi;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Redis адаптерът е споделен между независими JVM workers.
 * Atomic Lua script запазва по-дългия cooldown при едновременни 429 отговори;
 * без него по-кратък Retry-After може да прекъсне текущото изчакване.
 */
public final class RedisCooldownStore implements SharedRateLimitClient.CooldownStore {

    private static final DefaultRedisScript<Long> EXTEND_ONLY = new DefaultRedisScript<>(
            "local remaining = redis.call('PTTL', KEYS[1]) "
                    + "if remaining < tonumber(ARGV[1]) then "
                    + "redis.call('PSETEX', KEYS[1], ARGV[1], '429') "
                    + "end return 1",
            Long.class
    );

    private final StringRedisTemplate redis;

    public RedisCooldownStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public Duration remaining(SharedRateLimitClient.Scope scope) {
        Long millis = redis.getExpire(scope.redisKey(), TimeUnit.MILLISECONDS);
        return millis == null || millis <= 0 ? Duration.ZERO : Duration.ofMillis(millis);
    }

    @Override
    public void extendIfLonger(SharedRateLimitClient.Scope scope, Duration cooldown) {
        if (cooldown == null || !cooldown.isPositive()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        redis.execute(EXTEND_ONLY, List.of(scope.redisKey()),
                Long.toString(Math.max(1L, cooldown.toMillis())));
    }
}
