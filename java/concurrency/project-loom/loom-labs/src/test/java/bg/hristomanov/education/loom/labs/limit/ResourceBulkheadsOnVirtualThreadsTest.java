package bg.hristomanov.education.loom.labs.limit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Limits scarce downstream resources without requiring a custom VT scheduler. */
class ResourceBulkheadsOnVirtualThreadsTest {

    @Test
    void differentResourcesHaveIndependentCapsWithoutCustomCarrierPools() throws Exception {
        Semaphore databasePermits = new Semaphore(1);
        Semaphore remoteApiPermits = new Semaphore(2);
        AtomicInteger databaseActive = new AtomicInteger();
        AtomicInteger remoteActive = new AtomicInteger();
        AtomicInteger databasePeak = new AtomicInteger();
        AtomicInteger remotePeak = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                final boolean databaseCall = (i % 2 == 0);
                futures.add(executor.submit(() -> {
                    assertTrue(Thread.currentThread().isVirtual());
                    Semaphore limit = databaseCall ? databasePermits : remoteApiPermits;
                    AtomicInteger active = databaseCall ? databaseActive : remoteActive;
                    AtomicInteger peak = databaseCall ? databasePeak : remotePeak;
                    limit.acquire();
                    try {
                        int nowActive = active.incrementAndGet();
                        peak.accumulateAndGet(nowActive, Math::max);
                        Thread.sleep(8);
                        finished.incrementAndGet();
                        return null;
                    } finally {
                        active.decrementAndGet();
                        limit.release();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertEquals(36, finished.get());
        assertTrue(databasePeak.get() >= 1 && databasePeak.get() <= 1);
        assertTrue(remotePeak.get() >= 1 && remotePeak.get() <= 2);
        assertEquals(0, databaseActive.get());
        assertEquals(0, remoteActive.get());
        assertEquals(1, databasePermits.availablePermits());
        assertEquals(2, remoteApiPermits.availablePermits());
    }
}
