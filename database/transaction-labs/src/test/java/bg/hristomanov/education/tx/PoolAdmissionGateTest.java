package bg.hristomanov.education.tx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoolAdmissionGateTest {
    @Test
    void rejectsBeforeWaitingForPhysicalConnectionAndRecoversAfterRelease() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setPoolName("vm26-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:vm26_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(2_000);

        PoolAdmissionGate gate = new PoolAdmissionGate(1);
        CountDownLatch holdingConnection = new CountDownLatch(1);
        CountDownLatch releaseConnection = new CountDownLatch(1);

        try (HikariDataSource pool = new HikariDataSource(config);
             var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = worker.submit(() -> gate.execute(() -> {
                try (var connection = pool.getConnection();
                     var query = connection.prepareStatement("SELECT 42");
                     var results = query.executeQuery()) {
                    assertThat(results.next()).isTrue();
                    holdingConnection.countDown();
                    if (!releaseConnection.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test thread did not release the connection");
                    }
                    return results.getInt(1);
                }
            }));
            try {
                assertThat(holdingConnection.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                assertThat(gate.inFlight()).isEqualTo(1);

                // Rejected without even calling pool.getConnection().
                assertThatThrownBy(() -> gate.execute(() -> {
                    throw new AssertionError("Rejected work must not execute");
                })).isInstanceOf(RejectedExecutionException.class);

                assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
                assertThat(gate.rejected()).isEqualTo(1);
                assertThat(gate.peakInFlight()).isEqualTo(1);
            } finally {
                releaseConnection.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(gate.inFlight()).isZero();
            assertThat(gate.execute(() -> {
                try (var connection = pool.getConnection();
                     var query = connection.prepareStatement("SELECT 7");
                     var results = query.executeQuery()) {
                    assertThat(results.next()).isTrue();
                    return results.getInt(1);
                }
            })).isEqualTo(7);
            assertThat(gate.peakInFlight()).isEqualTo(1);
        }
    }

    @Test
    void failedOperationsReleaseTheirPermits() throws Exception {
        var gate = new PoolAdmissionGate(2);
        assertThatThrownBy(() -> gate.execute(() -> {
            throw new IllegalStateException("DB operation failed");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("DB operation failed");
        assertThat(gate.inFlight()).isZero();
        assertThat(gate.execute(() -> "ready")).isEqualTo("ready");
        assertThat(gate.rejected()).isZero();
    }

    @Test
    void rejectsInvalidBudgetAtConfigurationTime() {
        assertThatThrownBy(() -> new PoolAdmissionGate(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
