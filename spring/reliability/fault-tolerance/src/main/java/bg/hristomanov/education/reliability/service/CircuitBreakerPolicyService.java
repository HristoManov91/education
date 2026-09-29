package bg.hristomanov.education.reliability.service;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Circuit Breaker спира да изпраща calls към dependency, когато recent failure
 * или slow-call rate показва, че dependency-то е unhealthy.
 *
 * <p>Важно: slidingWindowSize НЕ е concurrency limit. За concurrent load
 * isolation използваме Bulkhead.</p>
 */
@Service
public class CircuitBreakerPolicyService {

    private final ControllableDownstreamService downstream;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerPolicyService(
            ControllableDownstreamService downstream,
            CircuitBreaker circuitBreaker
    ) {
        this.downstream = downstream;
        this.circuitBreaker = circuitBreaker;
    }

    public DownstreamResponse call() {
        Supplier<DownstreamResponse> decorated =
                CircuitBreaker.decorateSupplier(
                        circuitBreaker,
                        downstream::call
                );

        return decorated.get();
    }

    public CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }

    public void reset() {
        circuitBreaker.reset();
    }

    public void transitionToHalfOpenForLab() {
        circuitBreaker.transitionToHalfOpenState();
    }
}
