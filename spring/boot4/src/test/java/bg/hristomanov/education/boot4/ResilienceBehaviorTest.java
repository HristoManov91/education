package bg.hristomanov.education.boot4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверяваме semantics, а не само че Spring context се стартира:
 * retry има точно две повторения, concurrency лимитът е на външния proxy.
 */
@SpringBootTest
class ResilienceBehaviorTest {

    @Autowired
    ResilientCatalogClient client;

    @Autowired
    FlakyCatalogDependency dependency;

    @Autowired
    ConcurrencyProbe probe;

    @Test
    void retryUsesTwoAdditionalAttemptsAfterTheFirstCall() {
        dependency.reset();
        String result = client.readSafely();
        assertThat(result).isEqualTo("catalog-ready");
        assertThat(dependency.attempts()).isEqualTo(3);
    }

    @Test
    void onlyTwoCallsCanEnterLimitedMethodAtOnce() throws Exception {
        probe.resetPeak();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tasks.add(executor.submit(() -> probe.call(entered, release)));
            }
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(probe.peak()).isEqualTo(2);
            } finally {
                release.countDown();
            }
            for (Future<String> task : tasks) {
                assertThat(task.get(3, TimeUnit.SECONDS)).isEqualTo("ok");
            }
            assertThat(probe.peak()).isEqualTo(2);
        }
    }
    @Test
    void rejectPolicyFailsFastRatherThanQueueingAnExcessCaller() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> probe.callRejecting(entered, release));
            Future<String> second = executor.submit(() -> probe.callRejecting(entered, release));
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> probe.callRejecting(
                        new CountDownLatch(1), new CountDownLatch(0)))
                        .isInstanceOf(RejectedExecutionException.class);
            } finally {
                release.countDown();
            }
            assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo("ok");
        }
    }
}
