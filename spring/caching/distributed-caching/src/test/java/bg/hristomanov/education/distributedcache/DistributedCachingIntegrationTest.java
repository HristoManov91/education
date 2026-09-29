package bg.hristomanov.education.distributedcache;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class DistributedCachingIntegrationTest {

    private final RedisCachedProductService springCacheService;
    private final ProductSource source;
    private final NearCacheNodeFactory nodeFactory;
    private final StringRedisTemplate stringRedis;
    private final RedisConnectionFactory connectionFactory;
    private final CacheManager cacheManager;

    @Autowired
    DistributedCachingIntegrationTest(
            RedisCachedProductService springCacheService,
            ProductSource source,
            NearCacheNodeFactory nodeFactory,
            StringRedisTemplate stringRedis,
            RedisConnectionFactory connectionFactory,
            CacheManager cacheManager
    ) {
        this.springCacheService = springCacheService;
        this.source = source;
        this.nodeFactory = nodeFactory;
        this.stringRedis = stringRedis;
        this.connectionFactory = connectionFactory;
        this.cacheManager = cacheManager;
    }

    @BeforeEach
    void reset() {
        assumeTrue(redisAvailable(), "Redis integration lab skipped");

        try (RedisConnection connection =
                     connectionFactory.getConnection()) {
            connection.serverCommands()
                    .setConfig("maxmemory", "0");
            connection.serverCommands()
                    .setConfig(
                            "maxmemory-policy",
                            "noeviction"
                    );
            connection.serverCommands().flushDb();
        }

        source.reset();

        org.springframework.cache.Cache products =
                cacheManager.getCache("products");
        if (products != null) {
            products.clear();
        }
    }

    @Test
    void springCacheUsesSharedRedisWithTtlAndJsonSerialization() {
        Product first = springCacheService.get(1L);
        Product second = springCacheService.get(1L);

        assertThat(second).isEqualTo(first);
        assertThat(source.readCount()).isEqualTo(1);

        Long ttlSeconds =
                stringRedis.getExpire(
                        "edu:products::1"
                );

        assertThat(ttlSeconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(
                        RedisCacheConfiguration
                                .PRODUCT_TTL
                                .toSeconds()
                );
    }

    @Test
    void secondApplicationNodeCanWarmItsL1FromSharedRedisL2() {
        try (
                NearCacheNode nodeA =
                        nodeFactory.create("node-A");
                NearCacheNode nodeB =
                        nodeFactory.create("node-B")
        ) {
            Product fromA = nodeA.get(1L);
            Product fromB = nodeB.get(1L);

            assertThat(fromB).isEqualTo(fromA);
            assertThat(source.readCount()).isEqualTo(1);
            assertThat(nodeA.localContains(1L)).isTrue();
            assertThat(nodeB.localContains(1L)).isTrue();
        }
    }

    @Test
    void redisPubSubInvalidatesIndependentL1CopiesAfterWrite()
            throws Exception {
        try (
                NearCacheNode nodeA =
                        nodeFactory.create("node-A");
                NearCacheNode nodeB =
                        nodeFactory.create("node-B")
        ) {
            nodeA.get(1L);
            nodeB.get(1L);

            assertThat(source.readCount()).isEqualTo(1);

            Product updated = nodeA.updatePrice(
                    1L,
                    new BigDecimal("125.00")
            );

            waitUntil(
                    () -> !nodeB.localContains(1L),
                    Duration.ofSeconds(2)
            );

            Product fromB = nodeB.get(1L);

            assertThat(fromB).isEqualTo(updated);

            /*
             * Node B reloads from shared L2 Redis, not source of truth.
             */
            assertThat(source.readCount()).isEqualTo(1);
        }
    }

    @Test
    void twoColdNodesCanStillCreateDistributedStampede()
            throws Exception {
        source.readDelayMillis(150L);

        try (
                NearCacheNode nodeA =
                        nodeFactory.create("node-A");
                NearCacheNode nodeB =
                        nodeFactory.create("node-B");
                ExecutorService executor =
                        Executors.newVirtualThreadPerTaskExecutor()
        ) {
            CountDownLatch start = new CountDownLatch(1);

            Future<Product> a = executor.submit(() -> {
                start.await();
                return nodeA.get(1L);
            });

            Future<Product> b = executor.submit(() -> {
                start.await();
                return nodeB.get(1L);
            });

            start.countDown();

            assertThat(a.get(2, TimeUnit.SECONDS))
                    .isEqualTo(b.get(2, TimeUnit.SECONDS));

            /*
             * Both JVM-like nodes miss L1 and L2 before either source load
             * completes. Local single-flight cannot coordinate this.
             */
            assertThat(source.readCount()).isEqualTo(2);
        }
    }

    private boolean redisAvailable() {
        try (RedisConnection connection =
                     connectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(
                    connection.ping()
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void waitUntil(
            Callable<Boolean> condition,
            Duration timeout
    ) throws Exception {
        long deadline =
                System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(20L);
        }

        assertThat(condition.call())
                .as("condition before timeout")
                .isTrue();
    }
}
