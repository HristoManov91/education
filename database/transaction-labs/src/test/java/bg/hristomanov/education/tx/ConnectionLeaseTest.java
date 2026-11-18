package bg.hristomanov.education.tx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.SQLTransientConnectionException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionLeaseTest {

    @Test
    void aBorrowedJdbcConnectionOccupiesOneOfTheLimitedPoolSlots() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:pool_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(300);
        config.setPoolName("education-vm05");

        try (HikariDataSource pool = new HikariDataSource(config)) {
            assertThat(pool.getMaximumPoolSize()).isEqualTo(1);
            try (var first = pool.getConnection()) {
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                // Pool exhaustion is a resource-capacity symptom; no SQL is running here.
                assertThatThrownBy(pool::getConnection)
                        .isInstanceOf(SQLTransientConnectionException.class);
            }
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            try (var reused = pool.getConnection();
                 var select = reused.prepareStatement("SELECT 42");
                 var rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(42);
            }
        }
    }

    @Test
    void preparedStatementsBindValuesRatherThanInterpretingThemAsSql() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:bind_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        try (HikariDataSource pool = new HikariDataSource(config)) {
            try (var connection = pool.getConnection();
                 var create = connection.createStatement()) {
                create.execute("CREATE TABLE account_balance (id BIGINT PRIMARY KEY, quantity INT, version INT)");
                create.execute("INSERT INTO account_balance VALUES(1, 7, 0)");
            }
            assertThat(new ConnectionWork(pool).loadQuantity(1)).isEqualTo(7);
            assertThat(new ConnectionWork(pool).updateIfVersion(1, 8, 0)).isEqualTo(1);
            assertThat(new ConnectionWork(pool).updateIfVersion(1, 9, 0)).isZero();
            assertThat(new ConnectionWork(pool).loadQuantity(1)).isEqualTo(8);
        }
    }
}
