package bg.hristomanov.education.cache.service;

import bg.hristomanov.education.cache.cache.ProductCache;
import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.repository.ProductRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Write-behind / write-back стратегия.
 *
 * <p>Update-ът първо става видим в cache-а и се поставя в queue (опашка).
 * Persistent store-ът се обновява по-късно. Така write latency е ниска,
 * но приемаме eventual consistency (временна несъгласуваност) и риск от
 * загуба на неперсистирани записи при crash, ако queue-ът е само в паметта.</p>
 */
@Service
public class WriteBehindProductService implements CachingStrategyService {

    private final ProductRepository repository;
    private final ProductCache cache = new ProductCache("write-behind");
    private final ConcurrentLinkedQueue<PendingWrite> pendingWrites = new ConcurrentLinkedQueue<>();

    public WriteBehindProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public String strategyName() {
        return "write-behind";
    }

    @Override
    public Product get(long productId) {
        Optional<Product> cached = cache.get(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Product loaded = repository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
        cache.put(loaded);
        return loaded;
    }

    @Override
    public Product update(long productId, String name, BigDecimal price) {
        Product persistedNow = repository.peek(productId);
        if (persistedNow == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }

        Product visibleImmediately = new Product(
                productId,
                name,
                price,
                persistedNow.version() + pendingWritesFor(productId) + 1
        );

        /*
         * Caller-ът вижда новата стойност веднага от cache-а.
         * DB write още НЕ е извършен.
         */
        cache.put(visibleImmediately);
        pendingWrites.add(new PendingWrite(productId, name, price));
        return visibleImmediately;
    }

    /**
     * Реалните write-behind системи обикновено flush-ват на batch/interval/size.
     * Този метод е public нарочно, за да можем детерминирано да докажем
     * eventual consistency поведението в unit test и през HTTP demo.
     */
    public int flushPendingWrites() {
        int flushed = 0;
        PendingWrite pendingWrite = pendingWrites.poll();

        while (pendingWrite != null) {
            repository.save(pendingWrite.productId(), pendingWrite.name(), pendingWrite.price());
            flushed++;
            pendingWrite = pendingWrites.poll();
        }

        return flushed;
    }

    @Scheduled(fixedDelayString = "${cache-lab.write-behind.flush-ms:3000}")
    public void scheduledFlush() {
        flushPendingWrites();
    }

    public int pendingWriteCount() {
        return pendingWrites.size();
    }

    @Override
    public void clearCache() {
        cache.clear();
        pendingWrites.clear();
    }

    @Override
    public CacheStatistics cacheStatistics() {
        return cache.statistics();
    }

    private long pendingWritesFor(long productId) {
        return pendingWrites.stream()
                .filter(write -> write.productId() == productId)
                .count();
    }

    private record PendingWrite(long productId, String name, BigDecimal price) {
    }
}
