package bg.hristomanov.education.reliability;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import bg.hristomanov.education.reliability.downstream.PermanentDownstreamException;
import bg.hristomanov.education.reliability.service.CircuitBreakerPolicyService;
import bg.hristomanov.education.reliability.service.RetryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RetryPolicyServiceTest {

    private final ControllableDownstreamService downstream;
    private final RetryPolicyService retryPolicy;
    private final CircuitBreakerPolicyService circuitBreakerPolicy;

    @Autowired
    RetryPolicyServiceTest(
            ControllableDownstreamService downstream,
            RetryPolicyService retryPolicy,
            CircuitBreakerPolicyService circuitBreakerPolicy
    ) {
        this.downstream = downstream;
        this.retryPolicy = retryPolicy;
        this.circuitBreakerPolicy = circuitBreakerPolicy;
    }

    @BeforeEach
    void reset() {
        downstream.reset();
        circuitBreakerPolicy.reset();
    }

    @Test
    void transientFailuresAreRetriedUntilThirdAttemptSucceeds() {
        downstream.failTransiently(2);

        DownstreamResponse response = retryPolicy.call();

        assertThat(response.attempt()).isEqualTo(3);
        assertThat(downstream.callCount()).isEqualTo(3);
    }

    @Test
    void permanentFailureFailsFastInsteadOfRetrying() {
        downstream.failPermanently(true);

        assertThatThrownBy(retryPolicy::call)
                .isInstanceOf(PermanentDownstreamException.class);

        assertThat(downstream.callCount()).isEqualTo(1);
    }
}
