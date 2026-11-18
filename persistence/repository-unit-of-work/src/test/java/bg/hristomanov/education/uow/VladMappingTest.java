package bg.hristomanov.education.uow;

import bg.hristomanov.education.uow.domain.TrainingOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class VladMappingTest {
    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory factory;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearFixture() {
        jdbc.update("DELETE FROM training_order_lines");
        jdbc.update("DELETE FROM training_orders");
    }

    @Test
    void cascadePersistAndOrphanRemovalChangeActualChildRows() {
        long id = transactions.execute(status -> {
            var order = new TrainingOrder("VM10-CASCADE-" + UUID.randomUUID());
            order.addLine("alpha");
            order.addLine("beta");
            entityManager.persist(order); // CascadeType.ALL persists both children.
            entityManager.flush();
            return order.getId();
        });

        assertThat(childCount(id)).isEqualTo(2);

        transactions.executeWithoutResult(status -> {
            var managed = entityManager.find(TrainingOrder.class, id);
            assertThat(factory.getPersistenceUnitUtil().isLoaded(managed, "lines")).isFalse();
            assertThat(managed.getLines()).hasSize(2); // Lazy collection loaded inside tx.
            managed.removeLine("alpha");
            entityManager.flush(); // orphanRemoval DELETE, not child FK reassignment.
        });

        assertThat(childCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT description FROM training_order_lines WHERE order_id=?",
                String.class, id)).isEqualTo("beta");
    }

    @Test
    void lazyCollectionAccessAfterTransactionFailsWithoutOsiv() {
        long id = transactions.execute(status -> {
            var order = new TrainingOrder("VM10-LAZY-" + UUID.randomUUID());
            order.addLine("one");
            entityManager.persist(order);
            entityManager.flush();
            return order.getId();
        });

        TrainingOrder detached = transactions.execute(status -> {
            var order = entityManager.find(TrainingOrder.class, id);
            assertThat(factory.getPersistenceUnitUtil().isLoaded(order, "lines")).isFalse();
            return order;
        });

        assertThatThrownBy(() -> detached.getLines().size())
                .isInstanceOf(LazyInitializationException.class);
    }

    private int childCount(long parentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM training_order_lines WHERE order_id = ?",
                Integer.class, parentId);
    }
}
