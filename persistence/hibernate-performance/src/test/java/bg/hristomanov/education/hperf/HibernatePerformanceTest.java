package bg.hristomanov.education.hperf;

import bg.hristomanov.education.hperf.domain.IdentityEntry;
import bg.hristomanov.education.hperf.domain.PerfCustomer;
import bg.hristomanov.education.hperf.domain.PerfOrder;
import bg.hristomanov.education.hperf.domain.SequenceEntry;
import bg.hristomanov.education.hperf.read.PerfOrderReader;
import bg.hristomanov.education.hperf.repository.PerfCustomerRepository;
import bg.hristomanov.education.hperf.repository.PerfOrderRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class HibernatePerformanceTest {
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionFactory sessionFactory;
    @Autowired PerfOrderReader reader;
    @Autowired PerfCustomerRepository customers;
    @Autowired PerfOrderRepository orders;

    @BeforeEach
    void clean() {
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("delete from PerfOrder").executeUpdate();
            entityManager.createQuery("delete from PerfCustomer").executeUpdate();
            entityManager.createQuery("delete from IdentityEntry").executeUpdate();
            entityManager.createQuery("delete from SequenceEntry").executeUpdate();
            entityManager.clear();
        });
    }

    @Test
    void sequenceDefersInsertUntilFlushButIdentityNeedsRowToObtainId() {
        transactions.executeWithoutResult(status -> {
            SequenceEntry seq = new SequenceEntry("deferred");
            entityManager.persist(seq);
            assertThat(seq.getId()).isNotNull();
            // JDBC query bypasses Hibernate AUTO flush; same Spring transaction/connection.
            assertThat(jdbc.queryForObject(
                    "select count(*) from perf_sequence_entries", Integer.class)).isZero();
            entityManager.flush();
            assertThat(jdbc.queryForObject(
                    "select count(*) from perf_sequence_entries", Integer.class)).isEqualTo(1);

            IdentityEntry identity = new IdentityEntry("insert-needed");
            entityManager.persist(identity);
            assertThat(identity.getId()).isNotNull();
            assertThat(jdbc.queryForObject(
                    "select count(*) from perf_identity_entries", Integer.class)).isEqualTo(1);
        });
    }

    @Test
    void sequenceGeneratorActuallyExecutesJdbcBatchesAndIdentityDoesNot() {
        AtomicInteger sequenceBatchExecutions = new AtomicInteger();
        transactions.executeWithoutResult(status -> {
            entityManager.unwrap(Session.class).addEventListeners(batchCounter(sequenceBatchExecutions));
            IntStream.range(0, 23).forEach(i ->
                    entityManager.persist(new SequenceEntry("seq-" + i)));
            entityManager.flush();
            assertThat(jdbc.queryForObject(
                    "select count(*) from perf_sequence_entries", Integer.class)).isEqualTo(23);
        });
        // Hibernate 7.1 SessionEventListener observes executeBatch, not just statement prep.
        assertThat(sequenceBatchExecutions.get()).isBetween(2, 23);

        AtomicInteger identityBatchExecutions = new AtomicInteger();
        transactions.executeWithoutResult(status -> {
            entityManager.unwrap(Session.class).addEventListeners(batchCounter(identityBatchExecutions));
            IntStream.range(0, 4).forEach(i ->
                    entityManager.persist(new IdentityEntry("id-" + i)));
            assertThat(jdbc.queryForObject(
                    "select count(*) from perf_identity_entries", Integer.class)).isEqualTo(4);
        });
        assertThat(identityBatchExecutions.get()).isZero();
    }

    @Test
    void periodicFlushAndClearKeepsPersistenceContextBounded() {
        transactions.executeWithoutResult(status -> {
            SequenceEntry first = null;
            for (int i = 0; i < 53; i++) {
                SequenceEntry item = new SequenceEntry("chunk-" + i);
                if (i == 0) first = item;
                entityManager.persist(item);
                if ((i + 1) % 10 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            assertThat(entityManager.contains(first)).isFalse();
            entityManager.flush();
        });
        assertThat(jdbc.queryForObject(
                "select count(*) from perf_sequence_entries", Integer.class)).isEqualTo(53);
    }

    @Test
    void naiveLazyToOneProducesNPlusOneWhileFetchJoinAndEntityGraphUseOneSql() {
        seedCustomersAndOrders();
        transactions.executeWithoutResult(status -> {
            entityManager.clear();
            var stats = sessionFactory.getStatistics();
            stats.clear();
            var naive = reader.naiveOrders();
            assertThat(naive).hasSize(7);
            assertThat(naive.stream().map(o -> o.getCustomer().getName()).distinct()).hasSize(5);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(6);

            entityManager.clear();
            stats.clear();
            assertThat(reader.ordersWithCustomer()).hasSize(7)
                    .allSatisfy(order -> assertThat(order.getCustomer().getName()).startsWith("Customer-"));
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);

            entityManager.clear();
            stats.clear();
            assertThat(orders.findAllByOrderByIdAsc()).hasSize(7)
                    .allSatisfy(order -> assertThat(order.getCustomer().getName()).startsWith("Customer-"));
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        });
    }

    @Test
    void collectionFetchPaginationFailsFastAndTwoPhasePageLoadsExactlyTwoParents() {
        seedCustomersAndOrders();
        transactions.executeWithoutResult(status -> {
            entityManager.clear();
            assertThatThrownBy(() -> reader.unsafeCollectionPage(2))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("pagination");
            entityManager.clear();
            var page = reader.collectionPageByIds(2);
            assertThat(page).hasSize(2);
            assertThat(page.getFirst().getOrders()).isNotEmpty();
            assertThat(page.get(1).getOrders()).isNotEmpty();
        });
    }

    @Test
    void projectionAndKeysetCursorReturnStableOrderedWindows() {
        seedCustomersAndOrders();
        transactions.executeWithoutResult(status -> {
            entityManager.clear();
            var all = reader.offsetPage(0, 50);
            var first = reader.after(null, null, 3);
            var last = first.getLast();
            var second = reader.after(last.createdAt(), last.id(), 3);
            assertThat(all).hasSize(7);
            assertThat(first).containsExactlyElementsOf(all.subList(0, 3));
            assertThat(second).containsExactlyElementsOf(all.subList(3, 6));
            assertThat(reader.offsetPage(3, 3)).containsExactlyElementsOf(second);
        });
    }

    @Test
    void bulkDmlBypassesManagedEntityStateUntilExplicitClear() {
        seedCustomersAndOrders();
        transactions.executeWithoutResult(status -> {
            entityManager.clear();
            PerfOrder managed = reader.naiveOrders().getFirst();
            BigDecimal previous = managed.getTotal();
            BigDecimal replacement = new BigDecimal("990.00");
            assertThat(reader.bulkSetTotal(previous, replacement)).isEqualTo(1);
            assertThat(managed.getTotal()).isEqualByComparingTo(previous);
            entityManager.clear();
            assertThat(entityManager.find(PerfOrder.class, managed.getId()).getTotal())
                    .isEqualByComparingTo(replacement);
        });
    }

    @Test
    void springDataGetReferenceDefersSelectAndPageDoesCountButSliceDoesNot() {
        seedCustomersAndOrders();
        Long customerId = customers.findAll().getFirst().getId();
        transactions.executeWithoutResult(status -> {
            entityManager.clear();
            var stats = sessionFactory.getStatistics();
            stats.clear();
            var proxy = customers.getReferenceById(customerId);
            assertThat(proxy.getId()).isEqualTo(customerId);
            assertThat(stats.getPrepareStatementCount()).isZero();

            entityManager.clear();
            stats.clear();
            assertThat(customers.findById(customerId)).isPresent();
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);

            entityManager.clear();
            stats.clear();
            var page = orders.findAll(PageRequest.of(0, 2, Sort.by("id")));
            assertThat(page.getTotalElements()).isEqualTo(7);
            assertThat(page.getContent()).hasSize(2);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(2); // content + count

            entityManager.clear();
            stats.clear();
            var slice = orders.findByTotalGreaterThan(new BigDecimal("0.00"),
                    PageRequest.of(0, 2, Sort.by("id")));
            assertThat(slice.getContent()).hasSize(2);
            assertThat(slice.hasNext()).isTrue();
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        });
    }

    private static SessionEventListener batchCounter(AtomicInteger counter) {
        return new SessionEventListener() {
            @Override public void jdbcExecuteBatchStart() {
                counter.incrementAndGet();
            }
        };
    }

    private void seedCustomersAndOrders() {
        transactions.executeWithoutResult(status -> {
            LocalDateTime day = LocalDateTime.parse("2026-09-01T12:00:00");
            for (int i = 0; i < 5; i++) {
                var customer = new PerfCustomer("Customer-" + i);
                customer.placeOrder(new BigDecimal(String.valueOf(10 + i)), day.plusHours(i));
                if (i == 0) {
                    customer.placeOrder(new BigDecimal("88.00"), day.plusHours(i));
                    customer.placeOrder(new BigDecimal("89.00"), day.plusHours(i));
                }
                entityManager.persist(customer); // CascadeType.PERSIST on customer.orders
            }
            entityManager.flush();
            entityManager.clear();
        });
    }
}
