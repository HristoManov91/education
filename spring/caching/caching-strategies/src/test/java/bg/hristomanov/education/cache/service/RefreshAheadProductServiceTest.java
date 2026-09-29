package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.InMemoryProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshAheadProductServiceTest {

    @Test
    void refreshAheadUpdatesHotCachedValueBeforeNextRequestNeedsAColdLoad() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        RefreshAheadProductService service = new RefreshAheadProductService(repository);

        Product cachedVersionOne = service.get(1L);

        /*
         * Симулираме промяна от друг service/application instance.
         * Local cache-ът още не знае за нея.
         */
        Product persistedVersionTwo =
                repository.save(1L, "Mechanical Keyboard Pro", new BigDecimal("249.90"));

        Product staleBeforeRefresh = service.get(1L);
        assertThat(staleBeforeRefresh).isEqualTo(cachedVersionOne);

        int refreshed = service.refreshHotEntries();
        Product freshAfterRefresh = service.get(1L);

        assertThat(refreshed).isEqualTo(1);
        assertThat(freshAfterRefresh).isEqualTo(persistedVersionTwo);
        assertThat(freshAfterRefresh.version()).isEqualTo(2);
    }
}
