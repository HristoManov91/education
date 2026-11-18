package bg.hristomanov.education.distributedcache;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.framework.Advised;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
@SpringBootTest
class DistributedCachingIntegrationTest {

    private static final String SPRING_CACHE_KEY = "edu:products::1";

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
        // Without spring-aop, @EnableCaching may leave this bean unproxied:
        // the calls still return correct data but EACH call hits the source.
        assertThat(AopUtils.isAopProxy(springCacheService))
                .as("@Cacheable must be intercepted by a Spring AOP proxy")
                .isTrue();

        // A proxy alone is insufficient evidence: another advisor could create it.
        Assertions.assertTrue(hasCacheInterceptor(),
                () -> "Expected an actual Spring CacheInterceptor; "
                        + cacheDiagnostics("before first @Cacheable get"));

        Product first = springCacheService.get(1L);
        Product second = springCacheService.get(1L);

        assertThat(second).isEqualTo(first);
        // Expensive Redis reads and advisor inspection are performed ONLY on failure.
        Assertions.assertEquals(1, source.readCount(),
                () -> "Second @Cacheable get must hit Redis; "
                        + cacheDiagnostics("after two get(1L) calls"));

        Long ttlSeconds =
                stringRedis.getExpire(
                        SPRING_CACHE_KEY
                );

        assertThat(ttlSeconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(
                        DistributedRedisCacheConfiguration
                                .PRODUCT_TTL
                                .toSeconds()
                );
    }

    @Test
    void cacheEvictionViaProxyInvalidatesRedisAndForcesReload() {
        String cacheKey = SPRING_CACHE_KEY;

        Product first = springCacheService.get(1L);
        Assertions.assertTrue(Boolean.TRUE.equals(stringRedis.hasKey(cacheKey)),
                () -> "First @Cacheable get should populate Redis; "
                        + cacheDiagnostics("after first get(1L)"));
        Assertions.assertEquals(1, source.readCount(),
                () -> "First get should load source only once; "
                        + cacheDiagnostics("after first get(1L)"));

        // Exercise the same public proxied service that production uses,
        // not CacheManager directly: @CacheEvict must actually execute.
        springCacheService.evict(1L);
        Assertions.assertEquals(Boolean.FALSE, stringRedis.hasKey(cacheKey),
                () -> "@CacheEvict should remove the populated Redis key; "
                        + cacheDiagnostics("after evict(1L)"));

        Product reloaded = springCacheService.get(1L);
        assertThat(reloaded).isEqualTo(first);
        Assertions.assertEquals(2, source.readCount(),
                () -> "Reload after eviction should read from source; "
                        + cacheDiagnostics("after get following evict"));
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
        source.expectConcurrentReads(2);

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

    /**
     * Non-mutating on-failure diagnostics for the recurring CI flake (#95).
     * No Thread.sleep/retry: keep the original strict cache guarantees.
     * Read only the synthetic cache prefix; never dump Redis values or secrets.
     */
    private String cacheDiagnostics(String stage) {
        String advisors = springCacheService instanceof Advised advised
                ? Arrays.stream(advised.getAdvisors())
                        .map(advisor -> advisor.getAdvice().getClass().getSimpleName())
                        .collect(Collectors.joining(", "))
                : "not an Advised proxy";
        String cacheType = cacheManager.getCache("products") == null
                ? "no products cache"
                : cacheManager.getCache("products").getClass().getSimpleName();
        try {
            Boolean present = stringRedis.hasKey(SPRING_CACHE_KEY);
            Long ttlSeconds = stringRedis.getExpire(SPRING_CACHE_KEY);
            Set<String> namespacedKeys = stringRedis.keys("edu:products*");
            return "stage=" + stage
                    + ", sourceReads=" + source.readCount()
                    + ", isAopProxy=" + AopUtils.isAopProxy(springCacheService)
                    + ", advisors=" + advisors
                    + ", cacheManager=" + cacheManager.getClass().getSimpleName()
                    + ", cache=" + cacheType
                    + ", key=" + SPRING_CACHE_KEY
                    + ", keyPresent=" + present
                    + ", ttlSeconds=" + ttlSeconds
                    + ", namespacedKeys=" + namespacedKeys;
        } catch (RuntimeException exception) {
            return "stage=" + stage
                    + ", sourceReads=" + source.readCount()
                    + ", isAopProxy=" + AopUtils.isAopProxy(springCacheService)
                    + ", advisors=" + advisors
                    + ", cacheManager=" + cacheManager.getClass().getSimpleName()
                    + ", cache=" + cacheType
                    + ", Redis diagnostics failed: " + exception.getClass().getSimpleName();
        }
    }

    private boolean hasCacheInterceptor() {
        if (!(springCacheService instanceof Advised advised)) {
            return false;
        }
        return Arrays.stream(advised.getAdvisors())
                .anyMatch(advisor -> advisor.getAdvice() instanceof CacheInterceptor);
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
