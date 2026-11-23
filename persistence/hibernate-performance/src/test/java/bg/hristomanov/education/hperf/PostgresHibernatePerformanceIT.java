package bg.hristomanov.education.hperf;

import bg.hristomanov.education.hperf.domain.PerfCustomer;
import bg.hristomanov.education.hperf.domain.PerfOrder;
import bg.hristomanov.education.hperf.domain.SequenceEntry;
import bg.hristomanov.education.hperf.read.PerfOrderReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL, run ONLY with -Ppostgres-integration, Docker required. */
@SpringBootTest
@Testcontainers
class PostgresHibernatePerformanceIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withCommand("postgres", "-c", "shared_preload_libraries=pg_stat_statements",
                            "-c", "compute_query_id=on");

    @BeforeAll
    static void enablePgStatStatements() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
        }
    }

    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PerfOrderReader reader;

    @Test
    void postgresSequenceAllocationProducesActualJdbcBatchExecutions() {
        AtomicInteger batchExecutions = new AtomicInteger();
        transactions.executeWithoutResult(status -> {
            entityManager.unwrap(Session.class).addEventListeners(new SessionEventListener() {
                @Override public void jdbcExecuteBatchStart() {
                    batchExecutions.incrementAndGet();
                }
            });
            for (int i = 0; i < 23; i++) {
                entityManager.persist(new SequenceEntry("pg-seq-" + i));
            }
            entityManager.flush();
            assertThat(jdbc.queryForObject(
                    "select count(*) from perf_sequence_entries", Long.class)).isEqualTo(23L);
        });
        assertThat(batchExecutions.get()).isBetween(2, 23);
    }

    @Test
    void postgresNPlusOneAndJoinFetchHaveMeasurablyDifferentStatementCounts() {
        transactions.executeWithoutResult(status -> {
            for (int i = 0; i < 4; i++) {
                var c = new PerfCustomer("PG-Customer-" + i);
                c.placeOrder(BigDecimal.TEN, LocalDateTime.parse("2026-09-01T12:00:00"));
                entityManager.persist(c);
            }
            entityManager.flush();
            entityManager.clear();
            var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();
            reader.naiveOrders().forEach(o -> o.getCustomer().getName());
            assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
            entityManager.clear();
            stats.clear();
            assertThat(reader.ordersWithCustomer()).hasSize(4);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        });
    }

    @Test
    void postgresKeysetAndTwoPhaseCollectionFetchAreBounded() {
        transactions.executeWithoutResult(status -> {
            var date = LocalDateTime.parse("2026-09-01T12:00:00");
            for (int i = 0; i < 4; i++) {
                var c = new PerfCustomer("PG-Page-" + i);
                c.placeOrder(BigDecimal.valueOf(i + 1), date.plusDays(i));
                entityManager.persist(c);
            }
            entityManager.flush();
            entityManager.clear();
            var first = reader.after(null, null, 2);
            var cursor = first.getLast();
            var second = reader.after(cursor.createdAt(), cursor.id(), 2);
            assertThat(first).hasSize(2);
            assertThat(second).hasSize(2);
            assertThat(second).doesNotContainAnyElementsOf(first);
            assertThat(reader.collectionPageByIds(2)).hasSize(2)
                    .allSatisfy(c -> assertThat(c.getOrders()).isNotEmpty());
        });
    }

    @Test
    void statelessSessionInsertMultiplePersistsRowsAndAssignsGeneratedIds() {
        String marker = "stateless-bulk-" + UUID.randomUUID();
        List<SequenceEntry> entries = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            entries.add(new SequenceEntry(marker + "-" + index));
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        try (StatelessSession statelessSession = sessionFactory.openStatelessSession()) {
            Transaction transaction = statelessSession.beginTransaction();
            try {
                statelessSession.insertMultiple(entries);
                transaction.commit();
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw exception;
            }
        }

        assertThat(entries).allSatisfy(entry -> assertThat(entry.getId()).isNotNull());
        assertThat(jdbc.queryForObject(
                "select count(*) from perf_sequence_entries where description like ?",
                Long.class, marker + "%")).isEqualTo(25L);
    }

    @Test
    void statelessSessionDoesNotCascadeToAssociatedEntities() {
        String customerName = "stateless-no-cascade-" + UUID.randomUUID();
        PerfCustomer customer = new PerfCustomer(customerName);
        customer.placeOrder(BigDecimal.TEN, LocalDateTime.parse("2026-09-01T12:00:00"));

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        try (StatelessSession statelessSession = sessionFactory.openStatelessSession()) {
            Transaction transaction = statelessSession.beginTransaction();
            try {
                statelessSession.insert(customer);
                transaction.commit();
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw exception;
            }
        }

        assertThat(customer.getId()).isNotNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from perf_customers where id = ?", Long.class, customer.getId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from perf_orders where customer_id = ?", Long.class, customer.getId()))
                .isZero();
    }

    @Test
    void pgJdbcRewriteBatchedInsertsReducesServerStatementExecutions() throws SQLException {
        String plainTable = "pg_rewrite_plain";
        String rewrittenTable = "pg_rewrite_enabled";
        jdbc.execute("create table if not exists " + plainTable
                + " (id bigint primary key, payload text not null)");
        jdbc.execute("create table if not exists " + rewrittenTable
                + " (id bigint primary key, payload text not null)");
        jdbc.execute("truncate table " + plainTable);
        jdbc.execute("truncate table " + rewrittenTable);
        jdbc.execute("select pg_stat_statements_reset()");

        executePreparedInsertBatch(plainTable, false);
        executePreparedInsertBatch(rewrittenTable, true);

        Long plainCalls = jdbc.queryForObject("""
                select coalesce(sum(calls), 0)::bigint
                from pg_stat_statements
                where query ilike ?
                """, Long.class, "insert into " + plainTable + "%");
        Long rewrittenCalls = jdbc.queryForObject("""
                select coalesce(sum(calls), 0)::bigint
                from pg_stat_statements
                where query ilike ?
                """, Long.class, "insert into " + rewrittenTable + "%");

        assertThat(jdbc.queryForObject("select count(*) from " + plainTable, Long.class))
                .isEqualTo(64L);
        assertThat(jdbc.queryForObject("select count(*) from " + rewrittenTable, Long.class))
                .isEqualTo(64L);
        assertThat(plainCalls).isEqualTo(64L);
        assertThat(rewrittenCalls).isGreaterThan(0L).isLessThan(plainCalls);
    }

    private void executePreparedInsertBatch(String tableName, boolean rewriteBatchedInserts)
            throws SQLException {
        String baseUrl = POSTGRES.getJdbcUrl();
        String separator = baseUrl.contains("?") ? "&" : "?";
        String url = baseUrl + separator + "reWriteBatchedInserts=" + rewriteBatchedInserts;
        try (Connection connection = DriverManager.getConnection(
                url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into " + tableName + " (id, payload) values (?, ?)")) {
                for (int index = 1; index <= 64; index++) {
                    statement.setLong(1, index);
                    statement.setString(2, "payload-" + index);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void postgresCopyManagerParticipatesInTransactionRollback() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create temporary table copy_transaction_probe (
                        id bigint primary key,
                        label text not null
                    ) on commit preserve rows
                    """);
            connection.setAutoCommit(false);

            long copiedRows = connection.unwrap(PGConnection.class).getCopyAPI().copyIn(
                    "COPY copy_transaction_probe (id, label) FROM STDIN WITH (FORMAT csv)",
                    new StringReader("1,alpha\n2,beta\n"));

            assertThat(copiedRows).isEqualTo(2L);
            assertThat(countRows(statement)).isEqualTo(2L);

            connection.rollback();

            assertThat(countRows(statement)).isZero();
        }
    }

    private long countRows(Statement statement) throws SQLException {
        try (java.sql.ResultSet resultSet =
                     statement.executeQuery("select count(*) from copy_transaction_probe")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

}
