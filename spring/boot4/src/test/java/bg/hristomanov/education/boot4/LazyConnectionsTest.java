package bg.hristomanov.education.boot4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Доказваме разликата между logical transaction и physical connection.
 * Една операция във future не трябва да държи Hikari връзка преди SQL.
 */
@SpringBootTest
class LazyConnectionsTest {

    @Autowired
    TransactionBoundaryProbe boundaries;

    @Autowired
    DataSource dataSource;

    @Test
    void connectionIsNotBorrowedBeforeFirstSqlButIsHeldAfterSql() throws Exception {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch beforeEntered = new CountDownLatch(1);
            CountDownLatch beforeRelease = new CountDownLatch(1);
            Future<?> before = executor.submit(() -> boundaries.waitBeforeSql(beforeEntered, beforeRelease));
            try {
                assertThat(beforeEntered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            } finally {
                beforeRelease.countDown();
            }
            before.get(3, TimeUnit.SECONDS);

            CountDownLatch afterEntered = new CountDownLatch(1);
            CountDownLatch afterRelease = new CountDownLatch(1);
            Future<?> after = executor.submit(() -> boundaries.waitAfterSql(afterEntered, afterRelease));
            try {
                assertThat(afterEntered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            } finally {
                afterRelease.countDown();
            }
            after.get(3, TimeUnit.SECONDS);
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }
}
