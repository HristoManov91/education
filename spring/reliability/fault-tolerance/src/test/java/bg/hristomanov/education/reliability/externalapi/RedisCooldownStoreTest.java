package bg.hristomanov.education.reliability.externalapi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Реален Redis, не mock. CI workflow осигурява Redis service и
 * RUN_REDIS_TESTS=true. Локално можеш да стартираш Redis на localhost:6379.
 */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RedisCooldownStoreTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterEach
    void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void redisSharesCooldownBetweenWorkersAndNeverShortensExistingTtl() {
        RedisCooldownStore firstStore = new RedisCooldownStore(redis);
        RedisCooldownStore secondStore = new RedisCooldownStore(redis);
        SharedRateLimitClient.Scope scope = SharedRateLimitClient.Scope.of(
                "github", "org-" + UUID.randomUUID(), "token");

        firstStore.extendIfLonger(scope, Duration.ofSeconds(3));
        secondStore.extendIfLonger(scope, Duration.ofSeconds(8));
        firstStore.extendIfLonger(scope, Duration.ofSeconds(1));

        assertThat(firstStore.remaining(scope)).isGreaterThan(Duration.ofSeconds(6));

        SharedRateLimitClient secondWorker = new SharedRateLimitClient(secondStore,
                Clock.systemUTC(), Duration.ofSeconds(5), Duration.ofMinutes(10));
        AtomicInteger calls = new AtomicInteger();
        SharedRateLimitClient.Reply result = secondWorker.call(scope, () -> {
            calls.incrementAndGet();
            return SharedRateLimitClient.Reply.remote(200, "wrong", Map.of());
        });
        assertThat(result.fromSharedCooldown()).isTrue();
        assertThat(result.status()).isEqualTo(429);
        assertThat(calls).hasValue(0);
    }

    @Test
    void suspendedCredentialStateIsSharedAndMustBeExplicitlyRepaired() {
        RedisCredentialSuspensionStore worker1 = new RedisCredentialSuspensionStore(redis);
        RedisCredentialSuspensionStore worker2 = new RedisCredentialSuspensionStore(redis);
        CredentialRecovery.ConnectionId id = new CredentialRecovery.ConnectionId(
                "github", "org-" + UUID.randomUUID(), "integration-a");
        CredentialRecovery.ConnectionId other = new CredentialRecovery.ConnectionId(
                "github", id.tenantId(), "integration-b");

        assertThat(worker1.suspend(id)).isTrue();
        assertThat(worker2.suspend(id)).isFalse();
        assertThat(worker2.suspended(id)).isTrue();
        assertThat(worker1.suspended(other)).isFalse();

        worker2.restore(id);
        assertThat(worker1.suspended(id)).isFalse();
    }
}
