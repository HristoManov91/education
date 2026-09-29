package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.InMemoryProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAsideProductServiceTest {

    @Test
    void cacheAsideLoadsOnceAndInvalidatesAfterWrite() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        CacheAsideProductService service = new CacheAsideProductService(repository);

        Product first = service.get(1L);
        Product second = service.get(1L);

        assertThat(first).isEqualTo(second);
        assertThat(repository.readCount()).isEqualTo(1);

        Product updated = service.update(1L, "Mechanical Keyboard Pro", new BigDecimal("249.90"));
        Product afterUpdate = service.get(1L);

        assertThat(repository.writeCount()).isEqualTo(1);
        assertThat(repository.readCount()).isEqualTo(2);
        assertThat(afterUpdate).isEqualTo(updated);
        assertThat(afterUpdate.version()).isEqualTo(2);
    }

    @Test
    void naiveCacheAsideReturnsStaleDataWhenWriteDoesNotInvalidateCache() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        NaiveCacheAsideProductService service = new NaiveCacheAsideProductService(repository);

        Product cachedVersionOne = service.get(1L);
        Product persistedVersionTwo =
                service.update(1L, "Mechanical Keyboard Pro", new BigDecimal("249.90"));
        Product staleRead = service.get(1L);

        assertThat(persistedVersionTwo.version()).isEqualTo(2);
        assertThat(repository.peek(1L).version()).isEqualTo(2);

        /*
         * Това е correctness bug-ът, който искаме тестът да направи видим:
         * DB е v2, но cache-ът продължава да връща v1.
         */
        assertThat(staleRead).isEqualTo(cachedVersionOne);
        assertThat(staleRead.version()).isEqualTo(1);
    }
}
