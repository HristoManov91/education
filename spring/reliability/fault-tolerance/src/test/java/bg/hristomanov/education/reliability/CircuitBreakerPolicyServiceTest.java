package bg.hristomanov.education.reliability;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.TransientDownstreamException;
import bg.hristomanov.education.reliability.service.CircuitBreakerPolicyService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CircuitBreakerPolicyServiceTest {

    private final ControllableDownstreamService downstream;
    private final CircuitBreakerPolicyService circuitBreakerPolicy;

    @Autowired
    CircuitBreakerPolicyServiceTest(
            ControllableDownstreamService downstream,
            CircuitBreakerPolicyService circuitBreakerPolicy
    ) {
        this.downstream = downstream;
        this.circuitBreakerPolicy = circuitBreakerPolicy;
    }

    @BeforeEach
    void reset() {
        downstream.reset();
        circuitBreakerPolicy.reset();
    }

    @Test
    void circuitOpensAfterMinimumSampleShowsUnhealthyDependency() {
        for (int call = 0; call < 4; call++) {
            downstream.failTransiently(1);

            assertThatThrownBy(circuitBreakerPolicy::call)
                    .isInstanceOf(TransientDownstreamException.class);
        }

        assertThat(circuitBreakerPolicy.state())
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(downstream.callCount()).isEqualTo(4);

        assertThatThrownBy(circuitBreakerPolicy::call)
                .isInstanceOf(CallNotPermittedException.class);

        /*
         * OPEN circuit short-circuits before touching downstream.
         */
        assertThat(downstream.callCount()).isEqualTo(4);
    }

    @Test
    void successfulHalfOpenProbeCanCloseTheCircuitAgain() {
        for (int call = 0; call < 4; call++) {
            downstream.failTransiently(1);
            try {
                circuitBreakerPolicy.call();
            } catch (TransientDownstreamException ignored) {
                // Expected while creating the unhealthy sample.
            }
        }

        assertThat(circuitBreakerPolicy.state())
                .isEqualTo(CircuitBreaker.State.OPEN);

        downstream.failTransiently(0);
        circuitBreakerPolicy.transitionToHalfOpenForLab();

        circuitBreakerPolicy.call();
        circuitBreakerPolicy.call();

        assertThat(circuitBreakerPolicy.state())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
