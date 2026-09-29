package bg.hristomanov.education.reliability.api;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import bg.hristomanov.education.reliability.service.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/reliability")
public class ReliabilityLabController {

    private final ControllableDownstreamService downstream;
    private final RetryPolicyService retryPolicy;
    private final TimeoutPolicyService timeoutPolicy;
    private final CircuitBreakerPolicyService circuitBreakerPolicy;
    private final CombinedReliabilityService combinedPolicy;

    public ReliabilityLabController(
            ControllableDownstreamService downstream,
            RetryPolicyService retryPolicy,
            TimeoutPolicyService timeoutPolicy,
            CircuitBreakerPolicyService circuitBreakerPolicy,
            CombinedReliabilityService combinedPolicy
    ) {
        this.downstream = downstream;
        this.retryPolicy = retryPolicy;
        this.timeoutPolicy = timeoutPolicy;
        this.circuitBreakerPolicy = circuitBreakerPolicy;
        this.combinedPolicy = combinedPolicy;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        downstream.reset();
        circuitBreakerPolicy.reset();
        return state();
    }

    @GetMapping("/retry")
    public ReliabilityResult retry(
            @RequestParam(defaultValue = "2") int transientFailures
    ) {
        downstream.reset();
        downstream.failTransiently(transientFailures);

        DownstreamResponse response = retryPolicy.call();

        return result("retry", response);
    }

    @GetMapping("/timeout")
    public ReliabilityResult timeout(
            @RequestParam(defaultValue = "250") long delayMs
    ) {
        downstream.reset();
        downstream.delay(Duration.ofMillis(delayMs));

        DownstreamResponse response = timeoutPolicy.call();

        return result("timeout", response);
    }

    @PostMapping("/circuit/failure")
    public ReliabilityResult circuitFailure() {
        downstream.failTransiently(1);

        DownstreamResponse response = circuitBreakerPolicy.call();

        return result("circuit-breaker", response);
    }

    @PostMapping("/circuit/success")
    public ReliabilityResult circuitSuccess() {
        downstream.failPermanently(false);
        downstream.failTransiently(0);

        DownstreamResponse response = circuitBreakerPolicy.call();

        return result("circuit-breaker", response);
    }

    @GetMapping("/combined")
    public ReliabilityResult combined(
            @RequestParam(defaultValue = "2") int transientFailures,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        downstream.reset();
        combinedPolicy.resetCircuit();
        downstream.failTransiently(transientFailures);
        downstream.delay(Duration.ofMillis(delayMs));

        DownstreamResponse response = combinedPolicy.call();

        return result("combined", response);
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return Map.of(
                "downstreamCalls", downstream.callCount(),
                "maxObservedConcurrentCalls", downstream.maxObservedConcurrentCalls(),
                "interruptedCalls", downstream.interruptedCallCount(),
                "circuitState", circuitBreakerPolicy.state().name()
        );
    }

    private ReliabilityResult result(
            String policy,
            DownstreamResponse response
    ) {
        return new ReliabilityResult(
                policy,
                response.value(),
                downstream.callCount(),
                circuitBreakerPolicy.state().name()
        );
    }
}
