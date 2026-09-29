package bg.hristomanov.education.distributedcache;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class ProductSource {

    private final Map<Long, Product> products =
            new ConcurrentHashMap<>();
    private final AtomicInteger reads = new AtomicInteger();
    private volatile long readDelayMillis;

    public ProductSource() {
        reset();
    }

    public Product findRequired(long productId) {
        reads.incrementAndGet();
        simulateReadDelay();

        Product product = products.get(productId);
        if (product == null) {
            throw new IllegalArgumentException(
                    "Unknown product: " + productId
            );
        }
        return product;
    }

    public Product updatePrice(
            long productId,
            BigDecimal price
    ) {
        return products.compute(
                productId,
                (id, current) -> {
                    if (current == null) {
                        throw new IllegalArgumentException(
                                "Unknown product: " + id
                        );
                    }
                    return new Product(
                            current.id(),
                            current.name(),
                            price,
                            current.version() + 1
                    );
                }
        );
    }

    public int readCount() {
        return reads.get();
    }

    public void readDelayMillis(long readDelayMillis) {
        this.readDelayMillis = Math.max(0L, readDelayMillis);
    }

    public void reset() {
        products.clear();
        products.put(
                1L,
                new Product(
                        1L,
                        "Mechanical Keyboard",
                        new BigDecimal("100.00"),
                        1L
                )
        );
        reads.set(0);
        readDelayMillis = 0L;
    }

    private void simulateReadDelay() {
        if (readDelayMillis <= 0L) {
            return;
        }

        try {
            Thread.sleep(readDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Product source read was interrupted",
                    exception
            );
        }
    }
}
