package bg.hristomanov.education.reliability.service;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Един explicit composition на четирите policies.
 *
 * <p>Execution order отвън навътре:</p>
 *
 * <pre>
 * Retry
 *   → CircuitBreaker
 *      → Bulkhead
 *         → TimeLimiter
 *            → downstream
 * </pre>
 *
 * <p>Това означава, че всеки retry attempt минава през circuit breaker,
 * bulkhead и timeout. Circuit breaker-ът вижда всеки реален downstream attempt,
 * а не само крайния резултат след всички retries.</p>
 *
 * <p>Това НЕ е единственият правилен order. README сравнява алтернативите,
 * особено global timeout vs per-attempt timeout и CircuitBreaker outside Retry.</p>
 */
@Service
public class CombinedReliabilityService {

    private final ControllableDownstreamService downstream;
    private final TimeoutExecutor timeoutExecutor;
    private final Bulkhead bulkhead;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CombinedReliabilityService(
            ControllableDownstreamService downstream,
            TimeoutExecutor timeoutExecutor,
            Bulkhead bulkhead,
            CircuitBreaker circuitBreaker,
            Retry retry
    ) {
        this.downstream = downstream;
        this.timeoutExecutor = timeoutExecutor;
        this.bulkhead = bulkhead;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public DownstreamResponse call() {
        Supplier<DownstreamResponse> timed =
                () -> timeoutExecutor.execute(downstream::call);

        Supplier<DownstreamResponse> bulkheaded =
                Bulkhead.decorateSupplier(bulkhead, timed);

        Supplier<DownstreamResponse> circuitProtected =
                CircuitBreaker.decorateSupplier(circuitBreaker, bulkheaded);

        Supplier<DownstreamResponse> retried =
                Retry.decorateSupplier(retry, circuitProtected);

        return retried.get();
    }

    public long recordedFailedCalls() {
        return circuitBreaker.getMetrics().getNumberOfFailedCalls();
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    public void resetCircuit() {
        circuitBreaker.reset();
    }
}
