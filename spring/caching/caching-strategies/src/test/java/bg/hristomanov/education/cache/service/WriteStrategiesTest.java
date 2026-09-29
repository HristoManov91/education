package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.InMemoryProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WriteStrategiesTest {

    @Test
    void writeThroughUpdatesRepositoryAndCacheBeforeReturning() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        WriteThroughProductService service = new WriteThroughProductService(repository);

        Product updated = service.update(1L, "Mechanical Keyboard Pro", new BigDecimal("249.90"));
        int readsBeforeGet = repository.readCount();

        Product read = service.get(1L);

        assertThat(repository.peek(1L)).isEqualTo(updated);
        assertThat(read).isEqualTo(updated);
        assertThat(repository.readCount()).isEqualTo(readsBeforeGet);
    }

    @Test
    void writeBehindMakesNewValueVisibleBeforePersistentStoreIsUpdated() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        WriteBehindProductService service = new WriteBehindProductService(repository);

        Product persistedBeforeUpdate = repository.peek(1L);
        Product visibleImmediately =
                service.update(1L, "Mechanical Keyboard Pro", new BigDecimal("249.90"));

        assertThat(visibleImmediately.version()).isEqualTo(2);
        assertThat(service.get(1L)).isEqualTo(visibleImmediately);

        /*
         * ACK вече е върнат, но source of truth още съдържа старата версия.
         * Това е eventual consistency window-ът.
         */
        assertThat(repository.peek(1L)).isEqualTo(persistedBeforeUpdate);
        assertThat(service.pendingWriteCount()).isEqualTo(1);

        int flushed = service.flushPendingWrites();

        assertThat(flushed).isEqualTo(1);
        assertThat(repository.peek(1L).version()).isEqualTo(2);
        assertThat(repository.peek(1L).name()).isEqualTo("Mechanical Keyboard Pro");
        assertThat(service.pendingWriteCount()).isZero();
    }
}
