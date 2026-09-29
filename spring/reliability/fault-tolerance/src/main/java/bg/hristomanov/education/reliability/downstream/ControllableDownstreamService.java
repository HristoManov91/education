package bg.hristomanov.education.reliability.downstream;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Контролируем downstream fixture за лабораторията.
 *
 * <p>Един и същ dependency може да бъде конфигуриран като:
 * transiently failing, permanently failing или slow. Така сравняваме
 * reliability policies върху еднакъв downstream, а не върху различни demos.</p>
 */
@Component
public class ControllableDownstreamService {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger transientFailuresRemaining = new AtomicInteger();
    private final AtomicInteger activeCalls = new AtomicInteger();
    private final AtomicInteger maxObservedConcurrentCalls = new AtomicInteger();
    private final AtomicInteger interruptedCalls = new AtomicInteger();
    private final AtomicBoolean permanentFailure = new AtomicBoolean();

    private volatile Duration delay = Duration.ZERO;

    public DownstreamResponse call() {
        int attempt = calls.incrementAndGet();
        int active = activeCalls.incrementAndGet();
        maxObservedConcurrentCalls.accumulateAndGet(active, Math::max);

        try {
            simulateDelay();

            if (permanentFailure.get()) {
                throw new PermanentDownstreamException(
                        "Permanent downstream failure on attempt " + attempt
                );
            }

            int remaining = transientFailuresRemaining.getAndUpdate(
                    value -> value > 0 ? value - 1 : 0
            );

            if (remaining > 0) {
                throw new TransientDownstreamException(
                        "Transient downstream failure on attempt " + attempt
                );
            }

            return new DownstreamResponse("downstream-ok", attempt);
        } finally {
            activeCalls.decrementAndGet();
        }
    }

    public void reset() {
        calls.set(0);
        transientFailuresRemaining.set(0);
        activeCalls.set(0);
        maxObservedConcurrentCalls.set(0);
        interruptedCalls.set(0);
        permanentFailure.set(false);
        delay = Duration.ZERO;
    }

    public void failTransiently(int failures) {
        transientFailuresRemaining.set(Math.max(0, failures));
    }

    public void failPermanently(boolean fail) {
        permanentFailure.set(fail);
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public int callCount() {
        return calls.get();
    }

    public int maxObservedConcurrentCalls() {
        return maxObservedConcurrentCalls.get();
    }

    public int interruptedCallCount() {
        return interruptedCalls.get();
    }

    private void simulateDelay() {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            interruptedCalls.incrementAndGet();
            Thread.currentThread().interrupt();
            throw new DownstreamTimeoutException(
                    "Downstream call was interrupted",
                    exception
            );
        }
    }
}
