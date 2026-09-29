package bg.hristomanov.education.rediseviction;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class RedisEvictionIntegrationTest {

    private static final String ORIGINAL_MAXMEMORY = "0";
    private static final String ORIGINAL_POLICY = "noeviction";

    private final RedisAdmin redisAdmin;
    private final StringRedisTemplate redis;

    @Autowired
    RedisEvictionIntegrationTest(
            RedisAdmin redisAdmin,
            StringRedisTemplate redis
    ) {
        this.redisAdmin = redisAdmin;
        this.redis = redis;
    }

    @BeforeEach
    void prepareRedis() {
        assumeTrue(
                redisAdmin.ping(),
                "Redis is not running on localhost:6379; integration lab skipped"
        );

        resetRedis();
    }

    @AfterEach
    void restoreRedis() {
        if (!redisAdmin.ping()) {
            return;
        }
        resetRedis();
    }

    @Test
    void ttlExpirationDoesNotNeedMemoryPressure() throws Exception {
        redis.opsForValue().set(
                "ttl:short-lived",
                "value",
                Duration.ofMillis(150)
        );

        assertThat(redis.hasKey("ttl:short-lived")).isTrue();

        Thread.sleep(300);

        assertThat(redis.hasKey("ttl:short-lived")).isFalse();
        assertThat(redisAdmin.evictedKeys()).isZero();
    }

    @Test
    void noevictionRejectsNewWritesWhenMemoryBudgetIsExhausted() {
        redis.opsForValue().set("existing:key", "still-readable");

        long baseline = redisAdmin.usedMemoryBytes();
        redisAdmin.setConfig(
                "maxmemory",
                Long.toString(baseline + 700_000L)
        );
        redisAdmin.setConfig("maxmemory-policy", "noeviction");

        String largeValue = "x".repeat(64_000);

        assertThatThrownBy(() -> {
            for (int index = 0; index < 100; index++) {
                redis.opsForValue().set(
                        "noeviction:" + index,
                        largeValue
                );
            }
        }).isInstanceOf(DataAccessException.class);

        assertThat(redis.opsForValue().get("existing:key"))
                .isEqualTo("still-readable");
        assertThat(redisAdmin.evictedKeys()).isZero();
    }

    @Test
    void allKeysLruEvictsEntriesInsteadOfRejectingWrites() {
        long baseline = redisAdmin.usedMemoryBytes();
        redisAdmin.setConfig(
                "maxmemory",
                Long.toString(baseline + 850_000L)
        );
        redisAdmin.setConfig("maxmemory-policy", "allkeys-lru");

        String largeValue = "x".repeat(48_000);

        for (int index = 0; index < 100; index++) {
            redis.opsForValue().set(
                    "lru:" + index,
                    largeValue
            );

            /*
             * Keep a subset active. Redis LRU is approximate, so the test
             * intentionally does not assert one exact eviction victim.
             */
            if (index > 5) {
                redis.opsForValue().get(
                        "lru:" + (index % 5)
                );
            }
        }

        assertThat(redisAdmin.evictedKeys()).isPositive();
        assertThat(redisAdmin.databaseSize()).isLessThan(100);
    }

    @Test
    void lfuTracksHotterKeysWithHigherApproximateFrequency() {
        redisAdmin.setConfig("maxmemory-policy", "allkeys-lfu");

        redis.opsForValue().set("lfu:hot", "value");
        redis.opsForValue().set("lfu:cold", "value");

        for (int index = 0; index < 200; index++) {
            redis.opsForValue().get("lfu:hot");
        }

        redis.opsForValue().get("lfu:cold");

        long hotFrequency =
                redisAdmin.objectFrequency("lfu:hot");
        long coldFrequency =
                redisAdmin.objectFrequency("lfu:cold");

        assertThat(hotFrequency).isGreaterThan(coldFrequency);
    }

    @Test
    void volatileTtlNeverEvictsPersistentKeys() {
        redis.opsForValue().set(
                "persistent:reference-data",
                "must-survive"
        );

        long baseline = redisAdmin.usedMemoryBytes();
        redisAdmin.setConfig(
                "maxmemory",
                Long.toString(baseline + 850_000L)
        );
        redisAdmin.setConfig("maxmemory-policy", "volatile-ttl");

        String largeValue = "x".repeat(48_000);

        for (int index = 0; index < 100; index++) {
            redis.opsForValue().set(
                    "volatile:" + index,
                    largeValue,
                    Duration.ofMinutes(index + 1L)
            );
        }

        assertThat(redisAdmin.evictedKeys()).isPositive();
        assertThat(
                redis.opsForValue().get(
                        "persistent:reference-data"
                )
        ).isEqualTo("must-survive");
    }

    private void resetRedis() {
        redisAdmin.setConfig(
                "maxmemory",
                ORIGINAL_MAXMEMORY
        );
        redisAdmin.setConfig(
                "maxmemory-policy",
                ORIGINAL_POLICY
        );
        redisAdmin.flushDatabase();
        redisAdmin.setConfig("maxmemory-samples", "5");
        redisAdmin.setConfig("lfu-log-factor", "10");
        redisAdmin.setConfig("lfu-decay-time", "1");
    }
}
