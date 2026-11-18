package bg.hristomanov.education.sql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs only with -Ppostgres-integration, never in the root reactor's fast test phase. */
@Testcontainers
class PostgresSqlLabIT {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private static DataSource dataSource;

    @BeforeAll
    static void prepareRealDatabase() throws Exception {
        PGSimpleDataSource db = new PGSimpleDataSource();
        db.setURL(POSTGRES.getJdbcUrl());
        db.setUser(POSTGRES.getUsername());
        db.setPassword(POSTGRES.getPassword());
        dataSource = db;
        try (Connection connection = dataSource.getConnection()) {
            SqlScriptRunner.run(connection, "/sql/postgresql/schema.sql");
            SqlScriptRunner.run(connection, "/sql/postgresql/seed.sql");
            SqlScriptRunner.run(connection, "/sql/postgresql/performance.sql");
        }
    }

    @Test
    void joinsWindowsAndCteAlsoBehaveCorrectlyOnRealPostgresql() throws SQLException {
        SqlReadRepository repository = new SqlReadRepository(dataSource);
        assertThat(repository.customerIdsWithPaidOrders()).containsExactly(1L, 2L, 3L);
        assertThat(repository.customerIdsWithoutPaidOrders()).containsExactly(4L, 5L);
        assertThat(repository.topPaidCustomerPerRegion()).containsExactly(
                new SqlReadRepository.RegionLeader("BG", 2L, new BigDecimal("180.00")),
                new SqlReadRepository.RegionLeader("DE", 3L, new BigDecimal("90.00")));
        assertThat(repository.descendantsOfPostgres(1)).hasSize(4);
    }

    @Test
    void postgresEnforcesForeignKeyAndCheckConstraints() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO purchase_order(id, customer_id, external_ref, status, total, placed_at)
                VALUES (999, 999, 'BAD-FK', 'PAID', 10, CURRENT_TIMESTAMP)
                """)).isInstanceOf(SQLException.class)
                .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23503"));
        assertThatThrownBy(() -> execute("""
                INSERT INTO purchase_order(id, customer_id, external_ref, status, total, placed_at)
                VALUES (999, 1, 'BAD-CHECK', 'PAID', -10, CURRENT_TIMESTAMP)
                """)).isInstanceOf(SQLException.class)
                .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23514"));
        assertThatThrownBy(() -> execute("""
                INSERT INTO purchase_order(id, customer_id, external_ref, status, total, placed_at)
                VALUES (999, 1, 'A-01', 'PAID', 10, CURRENT_TIMESTAMP)
                """)).isInstanceOf(SQLException.class)
                .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23505"));
    }

    @Test
    void skewedStatisticsAndExecutionPlansAreMeasuredNotGuessed() throws SQLException {
        assertThat(scalar("SELECT COUNT(*) FROM task_event WHERE status = 'RETRY'")).isEqualTo(200);
        assertThat(scalar("SELECT COUNT(*) FROM task_event WHERE status = 'DONE'")).isEqualTo(19_800);
        assertThat(scalar("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'task_event' AND indexname = 'idx_task_event_tenant_status_created'
                """)).isEqualTo(1);
        String rarePlan = plan("SELECT * FROM task_event WHERE status = 'RETRY'");
        String commonPlan = plan("SELECT * FROM task_event WHERE status = 'DONE'");
        assertThat(rarePlan).contains("\"Plan\"", "\"Actual Rows\"");
        assertThat(commonPlan).contains("\"Plan\"", "\"Actual Rows\"");
        // Planner choices and elapsed times depend on machine, caches and statistics.
        // A stable integration test MUST NOT assert Index Scan vs Seq Scan.
        System.out.println("VM-04 rare predicate (measured): " + rarePlan);
        System.out.println("VM-04 common predicate (measured): " + commonPlan);
    }

    @Test
    void compositeIndexWorkloadReturnsBoundedAndDeterministicPage() throws SQLException {
        assertThat(scalar("""
                SELECT COUNT(*) FROM task_event
                WHERE tenant_id = 1 AND status = 'RETRY'
                """)).isEqualTo(200);
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT id FROM task_event
                     WHERE tenant_id = ? AND status = ?
                     ORDER BY created_at DESC, id DESC
                     LIMIT 10
                     """)) {
            statement.setInt(1, 1);
            statement.setString(2, "RETRY");
            try (var results = statement.executeQuery()) {
                int count = 0;
                long previous = Long.MAX_VALUE;
                while (results.next()) {
                    long id = results.getLong(1);
                    assertThat(id).isLessThan(previous);
                    previous = id;
                    count++;
                }
                assertThat(count).isEqualTo(10);
                assertThat(previous).isEqualTo(19_100L);
            }
        }
    }


    @Test
    void postgresPreservesEmptyStringAndDistinguishesItFromNull() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT length(''::text), ''::text IS NULL,
                            nullif(''::text, '') IS NULL
                     """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isZero();
            assertThat(rows.getBoolean(2)).isFalse();
            assertThat(rows.getBoolean(3)).isTrue();
        }
        // Oracle VARCHAR2 treats an empty string as NULL; do not generalize PG logic.
    }

    @Test
    void explicitNullOrderingAvoidsVendorDefaultAssumptions() throws SQLException {
        String fixture = """
                SELECT id FROM (VALUES (1, CAST(NULL AS INTEGER)), (2, 10), (3, 5))
                           AS items(id, rank_value)
                ORDER BY rank_value ASC
                """;
        assertThat(ids(fixture + " NULLS FIRST, id")).containsExactly(1, 3, 2);
        assertThat(ids(fixture + " NULLS LAST, id")).containsExactly(3, 2, 1);
        assertThat(ids(fixture + " NULLS LAST, id LIMIT 2")).containsExactly(3, 2);
    }

    @Test
    void sequenceGenerationIsNotRolledBackWithBusinessTransaction() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            // Private to this PostgreSQL session; does not interfere with other tests.
            statement.execute("CREATE TEMP SEQUENCE vm20_unique_seq START WITH 10");
            connection.setAutoCommit(false);
            long first = firstLong(connection, "SELECT nextval('vm20_unique_seq')");
            connection.rollback();
            long later = firstLong(connection, "SELECT nextval('vm20_unique_seq')");
            assertThat(later).isGreaterThan(first);
            connection.rollback();
        }
        // Neither PostgreSQL nor Oracle sequences promise gap-free values on rollback.
    }

    @Test
    void postgresJsonbAndStandardFetchFirstAreDifferentKindsOfPortability() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT '{\"order\":{\"status\":\"PAID\"}}'::jsonb #>> '{order,status}'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("PAID");
        }
        // FETCH FIRST is a portable SQL shape; ::jsonb and #>> are PG-specific.
        assertThat(ids("SELECT id FROM customer ORDER BY id FETCH FIRST 2 ROWS ONLY"))
                .containsExactly(1, 2);
    }

    private static java.util.List<Integer> ids(String sql) throws SQLException {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(rows.getInt(1));
            }
        }
        return result;
    }

    private static long firstLong(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                throw new SQLException("Expected one scalar result");
            }
            return rows.getLong(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long scalar(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String plan(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
