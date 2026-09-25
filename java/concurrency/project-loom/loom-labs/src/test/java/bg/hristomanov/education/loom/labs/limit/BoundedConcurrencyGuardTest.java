package bg.hristomanov.education.loom.labs.limit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedConcurrencyGuardTest {

    @Test
    void semaphoreLimitsScarceResourceInsteadOfPoolingVirtualThreads()
            throws InterruptedException {

        Semaphore resourcePermits = new Semaphore(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();

        try (StructuredTaskScope<Object, Void> scope =
                     StructuredTaskScope.open()) {

            for (int index = 0; index < 20; index++) {
                scope.fork(() -> {
                    resourcePermits.acquire();

                    try {
                        int nowActive = active.incrementAndGet();
                        maxObserved.accumulateAndGet(
                                nowActive,
                                Math::max);

                        Thread.sleep(20);
                        return null;

                    } finally {
                        active.decrementAndGet();
                        resourcePermits.release();
                    }
                });
            }

            scope.join();
        }

        assertEquals(2, maxObserved.get());
    }
}
