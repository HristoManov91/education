package bg.hristomanov.education.boot4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Два различни реда на операции в @Transactional. Транзакцията може да започне
 * без физическа JDBC връзка при lazy mode, но СЛЕД първия SQL връзката остава
 * заета до края на transaction boundary-то.
 */
@Service
public class TransactionBoundaryProbe {

    private final JdbcTemplate jdbc;

    public TransactionBoundaryProbe(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public void waitBeforeSql(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        await(release);
        jdbc.queryForObject("SELECT 1", Integer.class);
    }

    @Transactional(readOnly = true)
    public void waitAfterSql(CountDownLatch entered, CountDownLatch release) {
        jdbc.queryForObject("SELECT 1", Integer.class);
        entered.countDown();
        await(release);
    }

    private void await(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("lab release signal missing");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", exception);
        }
    }
}
