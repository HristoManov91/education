package bg.hristomanov.education.reliability;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamTimeoutException;
import bg.hristomanov.education.reliability.service.CircuitBreakerPolicyService;
import bg.hristomanov.education.reliability.service.TimeoutPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TimeoutPolicyServiceTest {

    private final ControllableDownstreamService downstream;
    private final TimeoutPolicyService timeoutPolicy;
    private final CircuitBreakerPolicyService circuitBreakerPolicy;

    @Autowired
    TimeoutPolicyServiceTest(
            ControllableDownstreamService downstream,
            TimeoutPolicyService timeoutPolicy,
            CircuitBreakerPolicyService circuitBreakerPolicy
    ) {
        this.downstream = downstream;
        this.timeoutPolicy = timeoutPolicy;
        this.circuitBreakerPolicy = circuitBreakerPolicy;
    }

    @BeforeEach
    void reset() {
        downstream.reset();
        circuitBreakerPolicy.reset();
    }

    @Test
    void slowCallExceedsCallerTimeBudget() throws Exception {
        downstream.delay(Duration.ofMillis(300));

        long startedNanos = System.nanoTime();

        assertThatThrownBy(timeoutPolicy::call)
                .isInstanceOf(DownstreamTimeoutException.class);

        long elapsedMillis =
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(elapsedMillis).isLessThan(250);
        assertThat(downstream.callCount()).isEqualTo(1);

        /*
         * Future cancellation sends interruption. Give the virtual thread a
         * short scheduling window to observe it; this is not the core timeout
         * assertion, only proof that our fixture cooperates with cancellation.
         */
        for (int attempt = 0;
             attempt < 20 && downstream.interruptedCallCount() == 0;
             attempt++) {
            Thread.sleep(5);
        }

        assertThat(downstream.interruptedCallCount()).isEqualTo(1);
    }
}
