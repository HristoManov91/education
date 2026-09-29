package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "cache-lab.write-behind.flush-ms=60000",
        "cache-lab.refresh-ahead.refresh-ms=60000"
})
class SpringCacheProductServiceTest {

    private final CachingStrategyService service;
    private final ProductRepository repository;

    SpringCacheProductServiceTest(
            @Qualifier("springCacheProductService") CachingStrategyService service,
            ProductRepository repository
    ) {
        this.service = service;
        this.repository = repository;
    }

    @BeforeEach
    void reset() {
        service.clearCache();
        repository.reset();
    }

    @Test
    void cacheableAvoidsRepeatedRepositoryReadsAndCachePutRefreshesValue() {
        Product first = service.get(1L);
        Product second = service.get(1L);

        assertThat(first).isEqualTo(second);
        assertThat(repository.readCount()).isEqualTo(1);

        Product updated = service.update(1L, "Mechanical Keyboard Pro", new BigDecimal("249.90"));
        Product afterUpdate = service.get(1L);

        assertThat(afterUpdate).isEqualTo(updated);
        assertThat(repository.readCount()).isEqualTo(1);
        assertThat(repository.writeCount()).isEqualTo(1);
    }

    @Test
    void cacheableSyncCoalescesConcurrentMissesForTheSameKey() throws Exception {
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Product>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int index = 0; index < callers; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.get(1L);
                }));
            }

            start.countDown();

            for (Future<Product> future : futures) {
                assertThat(future.get().id()).isEqualTo(1L);
            }
        }

        /*
         * Без sync=true burst от concurrent cache misses може да изпълни
         * expensive method-а няколко пъти. Тук provider-ът coalesce-ва load-а.
         */
        assertThat(repository.readCount()).isEqualTo(1);
    }
}
