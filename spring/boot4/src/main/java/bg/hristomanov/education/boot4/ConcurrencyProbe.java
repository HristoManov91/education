package bg.hristomanov.education.boot4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

/**
 * @ConcurrencyLimit ограничава достъпа до scarce downstream resource, а НЕ
 * броя Virtual Threads. Тестът държи първите две операции отворени и доказва
 * че трета не може да премине този method boundary едновременно.
 */
@Service
public class ConcurrencyProbe {

    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    @ConcurrencyLimit(2)
    public String call(CountDownLatch entered, CountDownLatch release) {
        return runCall(entered, release);
    }

    private String runCall(CountDownLatch entered, CountDownLatch release) {
        int current = active.incrementAndGet();
        peak.accumulateAndGet(current, Math::max);
        try {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("lab release signal missing");
            }
            return "ok";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", exception);
        } finally {
            active.decrementAndGet();
        }
    }

    /**
     * От Spring Framework 7.0.3: алтернатива на BLOCK.
     * При претоварване REJECT отхвърля веднага, вместо да държи задачата в чакане.
     */
    @ConcurrencyLimit(value = 2, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
    public String callRejecting(CountDownLatch entered, CountDownLatch release) {
        return runCall(entered, release);
    }

    public int peak() {
        return peak.get();
    }

    public void resetPeak() {
        peak.set(0);
    }
}
