package bg.hristomanov.education.reliability;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import bg.hristomanov.education.reliability.downstream.DownstreamTimeoutException;
import bg.hristomanov.education.reliability.service.CombinedReliabilityService;
import bg.hristomanov.education.reliability.service.CircuitBreakerPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CombinedReliabilityServiceTest {

    private final ControllableDownstreamService downstream;
    private final CombinedReliabilityService combinedPolicy;
    private final CircuitBreakerPolicyService circuitBreakerPolicy;

    @Autowired
    CombinedReliabilityServiceTest(
            ControllableDownstreamService downstream,
            CombinedReliabilityService combinedPolicy,
            CircuitBreakerPolicyService circuitBreakerPolicy
    ) {
        this.downstream = downstream;
        this.combinedPolicy = combinedPolicy;
        this.circuitBreakerPolicy = circuitBreakerPolicy;
    }

    @BeforeEach
    void reset() {
        downstream.reset();
        combinedPolicy.resetCircuit();
    }

    @Test
    void everyRetryAttemptPassesThroughTheCircuitBreaker() {
        downstream.failTransiently(2);

        DownstreamResponse response = combinedPolicy.call();

        assertThat(response.attempt()).isEqualTo(3);
        assertThat(downstream.callCount()).isEqualTo(3);

        /*
         * При избрания order CircuitBreaker е inside Retry.
         * Той вижда двата неуспешни physical attempts.
         */
        assertThat(combinedPolicy.recordedFailedCalls()).isEqualTo(2);
    }

    @Test
    void timeoutIsPerAttemptAndRetryCanConsumeThreeTimeBudgets() {
        downstream.delay(Duration.ofMillis(250));

        assertThatThrownBy(combinedPolicy::call)
                .isInstanceOf(DownstreamTimeoutException.class);

        assertThat(downstream.callCount()).isEqualTo(3);
        assertThat(combinedPolicy.recordedFailedCalls()).isEqualTo(3);
        assertThat(circuitBreakerPolicy.state().name())
                .isIn("CLOSED", "OPEN");
    }
}
