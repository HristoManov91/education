package bg.hristomanov.education.hperf;

import bg.hristomanov.education.hperf.domain.PerfCustomer;
import bg.hristomanov.education.hperf.domain.PerfOrder;
import bg.hristomanov.education.hperf.domain.SequenceEntry;
import bg.hristomanov.education.hperf.read.PerfOrderReader;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL, run ONLY with -Ppostgres-integration, Docker required. */
@SpringBootTest
@Testcontainers
class PostgresHibernatePerformanceIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionFactory sessionFactory;
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
            var stats = sessionFactory.getStatistics();
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
}
