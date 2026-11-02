package bg.hristomanov.education.build.ci;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in JUnit Jupiter concurrency lab. Maven/CI does not enable global
 * parallel execution. ResourceLock serializes only the unsafe shared fixture;
 * independent tests can execute concurrently.
 */
@Execution(ExecutionMode.CONCURRENT)
class ParallelCiFixtureTest {

    private static int sharedCounter;

    @Test
    @ResourceLock(value = "ci-shared-counter", mode = ResourceAccessMode.READ_WRITE)
    void firstSharedFixtureUsesExplicitExclusiveOwnership() {
        sharedCounter = 0;
        for (int i = 0; i < 100; i++) {
            sharedCounter++;
        }
        assertThat(sharedCounter).isEqualTo(100);
    }

    @Test
    @ResourceLock(value = "ci-shared-counter", mode = ResourceAccessMode.READ_WRITE)
    void secondSharedFixtureUsesTheSameLockAndNoOrderAssumptions() {
        sharedCounter = 0;
        for (int i = 0; i < 250; i++) {
            sharedCounter++;
        }
        assertThat(sharedCounter).isEqualTo(250);
    }

    @Test
    void independentFixturesDoNotNeedSharedMutableStaticState() {
        Map<String, Integer> localTotals = Map.of("A", 12, "B", 18);
        assertThat(localTotals.values()).containsExactlyInAnyOrder(12, 18);
    }

    @Test
    void independentBusinessInputProducesDeterministicResults() {
        int sum = 0;
        for (int i = 1; i <= 100; i++) {
            sum += i;
        }
        assertThat(sum).isEqualTo(5_050);
    }
}
