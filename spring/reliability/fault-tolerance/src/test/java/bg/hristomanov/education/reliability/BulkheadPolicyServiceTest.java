package bg.hristomanov.education.reliability;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.service.BulkheadPolicyService;
import bg.hristomanov.education.reliability.service.CircuitBreakerPolicyService;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BulkheadPolicyServiceTest {

    private final ControllableDownstreamService downstream;
    private final BulkheadPolicyService bulkheadPolicy;
    private final CircuitBreakerPolicyService circuitBreakerPolicy;

    @Autowired
    BulkheadPolicyServiceTest(
            ControllableDownstreamService downstream,
            BulkheadPolicyService bulkheadPolicy,
            CircuitBreakerPolicyService circuitBreakerPolicy
    ) {
        this.downstream = downstream;
        this.bulkheadPolicy = bulkheadPolicy;
        this.circuitBreakerPolicy = circuitBreakerPolicy;
    }

    @BeforeEach
    void reset() {
        downstream.reset();
        circuitBreakerPolicy.reset();
    }

    @Test
    void onlyTwoConcurrentCallsMayEnterTheDownstream() throws Exception {
        int callers = 6;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        downstream.delay(Duration.ofMillis(200));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < callers; index++) {
                futures.add(
                        executor.submit(() -> {
                            start.await();

                            try {
                                bulkheadPolicy.call();
                                return true;
                            } catch (BulkheadFullException exception) {
                                return false;
                            }
                        })
                );
            }

            start.countDown();

            int successes = 0;
            int rejections = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(2, TimeUnit.SECONDS)) {
                    successes++;
                } else {
                    rejections++;
                }
            }

            assertThat(successes).isEqualTo(2);
            assertThat(rejections).isEqualTo(4);
            assertThat(downstream.maxObservedConcurrentCalls()).isLessThanOrEqualTo(2);
            assertThat(downstream.callCount()).isEqualTo(2);
        }
    }
}
