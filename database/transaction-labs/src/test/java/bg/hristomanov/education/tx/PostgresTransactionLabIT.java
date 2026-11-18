package bg.hristomanov.education.tx;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL evidence: isolation and row-lock behavior MUST NOT be inferred from H2. */
@Testcontainers
class PostgresTransactionLabIT {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private static DataSource source;

    @BeforeAll
    static void initialize() throws SQLException {
        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setURL(POSTGRES.getJdbcUrl());
        pg.setUser(POSTGRES.getUsername());
        pg.setPassword(POSTGRES.getPassword());
        source = pg;
        execute("""
                CREATE TABLE account_balance (
                    id BIGINT PRIMARY KEY,
                    quantity INT NOT NULL CHECK (quantity >= 0),
                    version INT NOT NULL DEFAULT 0
                )
                """);
        execute("""
                CREATE TABLE work_item (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(12) NOT NULL
                )
                """);
    }

    @BeforeEach
    void reset() throws SQLException {
        execute("TRUNCATE work_item, account_balance");
        execute("INSERT INTO account_balance VALUES(1, 10, 0), (2, 10, 0)");
        execute("INSERT INTO work_item VALUES(1, 'READY'), (2, 'READY'), (3, 'READY')");
    }

    @Test
    void uncommittedUpdateIsInvisibleAndRollbackRestoresPreviousValue() throws SQLException {
        try (var writer = source.getConnection(); var reader = source.getConnection()) {
            writer.setAutoCommit(false);
            modify(writer, 1, 99);
            assertThat(readQuantity(reader, 1)).isEqualTo(10);
            writer.rollback();
            assertThat(readQuantity(reader, 1)).isEqualTo(10);
        }
    }

    @Test
    void readCommittedGetsANewSnapshotOnSecondStatement() throws SQLException {
        try (var reader = source.getConnection(); var writer = source.getConnection()) {
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            reader.setAutoCommit(false);
            assertThat(readQuantity(reader, 1)).isEqualTo(10);
            modify(writer, 1, 12); // separate committed transaction
            assertThat(readQuantity(reader, 1)).isEqualTo(12);
            reader.rollback();
        }
    }

    @Test
    void repeatableReadKeepsOriginalSnapshotForTheSameTransaction() throws SQLException {
        try (var reader = source.getConnection(); var writer = source.getConnection()) {
            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            reader.setAutoCommit(false);
            assertThat(readQuantity(reader, 1)).isEqualTo(10);
            modify(writer, 1, 12);
            assertThat(readQuantity(reader, 1)).isEqualTo(10);
            reader.rollback();
        }
        try (var fresh = source.getConnection()) {
            assertThat(readQuantity(fresh, 1)).isEqualTo(12);
        }
    }

    @Test
    void naiveReadModifyWriteSilentlyLosesOneOfTwoChanges() throws SQLException {
        try (var first = source.getConnection(); var second = source.getConnection()) {
            int firstSeen = readQuantity(first, 1);
            int secondSeen = readQuantity(second, 1);
            modify(first, 1, firstSeen + 1);
            modify(second, 1, secondSeen + 1);
            assertThat(readQuantity(first, 1)).isEqualTo(11); // expected 12, one increment lost
        }
    }

    @Test
    void optimisticCompareAndSetPreventsTheLostUpdate() throws SQLException {
        ConnectionWork repository = new ConnectionWork(source);
        // Two callers both saw version 0. Only one write can succeed.
        assertThat(repository.updateIfVersion(1, 11, 0)).isEqualTo(1);
        assertThat(repository.updateIfVersion(1, 12, 0)).isZero();
        assertThat(repository.loadQuantity(1)).isEqualTo(11);
        assertThat(readVersion(1)).isEqualTo(1);
    }

    @Test
    void nowaitRejectsConflictingPessimisticLockInsteadOfWaiting() throws SQLException {
        try (var first = source.getConnection(); var second = source.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            selectLocking(first, "SELECT id FROM account_balance WHERE id=1 FOR UPDATE");
            assertThatThrownBy(() ->
                    selectLocking(second, "SELECT id FROM account_balance WHERE id=1 FOR UPDATE NOWAIT"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("55P03"));
            second.rollback(); // PostgreSQL marks transaction as failed after the NOWAIT error.
            first.rollback();
        }
    }

    @Test
    void skipLockedLetsTwoWorkersClaimDifferentRowsWithoutDoubleClaim() throws SQLException {
        try (var first = source.getConnection(); var second = source.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            int firstId = selectLocking(first, """
                    SELECT id FROM work_item WHERE status='READY'
                    ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """);
            int secondId = selectLocking(second, """
                    SELECT id FROM work_item WHERE status='READY'
                    ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """);
            assertThat(firstId).isEqualTo(1);
            assertThat(secondId).isEqualTo(2);
            updateWorkStatus(first, firstId);
            updateWorkStatus(second, secondId);
            first.commit();
            second.commit();
        }
        assertThat(countClaimed()).isEqualTo(2);
    }

    @Test
    void opposingLockOrderCausesDeadlockAndDatabaseAbortsOneTransaction() throws Exception {
        // Both sessions hold their first row lock before either requests the second.
        CyclicBarrier bothHoldingFirstRow = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<String> a = workers.submit(deadlockingWorker(1, 2, bothHoldingFirstRow));
            Future<String> b = workers.submit(deadlockingWorker(2, 1, bothHoldingFirstRow));
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "40P01");
        }
    }

    private static Callable<String> deadlockingWorker(
            int firstId, int secondId, CyclicBarrier bothHoldingFirstRow) {
        return () -> {
            try (var connection = source.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // Prevent an indefinitely hung CI test if the DB cannot detect the cycle.
                    try (var statement = connection.createStatement()) {
                        statement.execute("SET LOCAL statement_timeout = '8s'");
                    }
                    increment(connection, firstId);
                    bothHoldingFirstRow.await(5, TimeUnit.SECONDS);
                    increment(connection, secondId);
                    connection.commit();
                    return "OK";
                } catch (SQLException error) {
                    connection.rollback();
                    return error.getSQLState();
                } finally {
                    if (!connection.getAutoCommit()) {
                        connection.rollback(); // No residual open transaction on the pooled connection.
                    }
                }
            }
        };
    }

    private static void increment(Connection c, int id) throws SQLException {
        try (var p = c.prepareStatement("UPDATE account_balance SET quantity=quantity+1 WHERE id=?")) {
            p.setInt(1, id);
            assertThat(p.executeUpdate()).isEqualTo(1);
        }
    }

    private static void updateWorkStatus(Connection c, int id) throws SQLException {
        try (var p = c.prepareStatement("UPDATE work_item SET status='CLAIMED' WHERE id=?")) {
            p.setInt(1, id);
            assertThat(p.executeUpdate()).isEqualTo(1);
        }
    }

    private static int selectLocking(Connection c, String query) throws SQLException {
        try (var statement = c.createStatement(); var rs = statement.executeQuery(query)) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private static int readQuantity(Connection c, int id) throws SQLException {
        try (var statement = c.prepareStatement("SELECT quantity FROM account_balance WHERE id=?")) {
            statement.setInt(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    private static void modify(Connection c, int id, int quantity) throws SQLException {
        try (var p = c.prepareStatement("UPDATE account_balance SET quantity=? WHERE id=?")) {
            p.setInt(1, quantity);
            p.setInt(2, id);
            assertThat(p.executeUpdate()).isEqualTo(1);
        }
    }

    private static int readVersion(int id) throws SQLException {
        try (var c = source.getConnection();
             var statement = c.prepareStatement("SELECT version FROM account_balance WHERE id=?")) {
            statement.setInt(1, id);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countClaimed() throws SQLException {
        try (var c = source.getConnection();
             var statement = c.createStatement();
             var rs = statement.executeQuery("SELECT COUNT(*) FROM work_item WHERE status='CLAIMED'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (var c = source.getConnection(); var statement = c.createStatement()) {
            statement.execute(sql);
        }
    }
}
