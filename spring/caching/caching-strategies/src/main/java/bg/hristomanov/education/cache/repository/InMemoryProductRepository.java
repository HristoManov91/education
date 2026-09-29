package bg.hristomanov.education.cache.repository;

import bg.hristomanov.education.cache.domain.Product;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Контролируем заместител на бавна база данни.
 *
 * <p>Броячите са умишлено част от лабораторията: вместо да твърдим,
 * че cache-ът намалява database reads, можем директно да го докажем.</p>
 */
@Repository
public class InMemoryProductRepository implements ProductRepository {

    public static final long DEFAULT_PRODUCT_ID = 1L;

    private final ConcurrentHashMap<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();

    public InMemoryProductRepository() {
        reset();
    }

    @Override
    public Optional<Product> findById(long productId) {
        reads.incrementAndGet();
        simulateDatabaseLatency();
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public Product save(long productId, String name, BigDecimal price) {
        writes.incrementAndGet();
        simulateDatabaseLatency();

        Product current = products.get(productId);
        long nextVersion = current == null ? 1 : current.version() + 1;
        Product persisted = new Product(productId, name, price, nextVersion);
        products.put(productId, persisted);
        return persisted;
    }

    @Override
    public void seed(Product product) {
        products.put(product.id(), product);
    }

    /**
     * Чете store-а без да увеличава read counter-а.
     * Това е диагностичен hook само за лабораторията и тестовете.
     */
    @Override
    public Product peek(long productId) {
        return products.get(productId);
    }

    @Override
    public int readCount() {
        return reads.get();
    }

    @Override
    public int writeCount() {
        return writes.get();
    }

    @Override
    public void reset() {
        products.clear();
        products.put(
                DEFAULT_PRODUCT_ID,
                new Product(DEFAULT_PRODUCT_ID, "Mechanical Keyboard", new BigDecimal("199.90"), 1)
        );
        reads.set(0);
        writes.set(0);
    }

    private void simulateDatabaseLatency() {
        try {
            Thread.sleep(35);
        } catch (InterruptedException e) {
            /*
             * sleep() изчиства interrupt flag-а, когато хвърли InterruptedException.
             * Възстановяваме го, за да не изгубим cooperative cancellation signal-а.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Repository operation was interrupted", e);
        }
    }
}
