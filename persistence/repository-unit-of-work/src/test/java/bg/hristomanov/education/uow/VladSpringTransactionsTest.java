package bg.hristomanov.education.uow;

import bg.hristomanov.education.uow.service.VladSelfInvocationProbe;
import bg.hristomanov.education.uow.service.VladTransactionInnerService;
import bg.hristomanov.education.uow.service.VladTransactionOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 tests prove Spring transaction proxy semantics and JDBC savepoints.
 * They are NOT evidence for PostgreSQL/Oracle MVCC or driver-specific locking.
 * The test methods themselves intentionally do NOT use @Transactional.
 */
@SpringBootTest
class VladSpringTransactionsTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired VladTransactionOrchestrationService orchestration;
    @Autowired VladTransactionInnerService inner;
    @Autowired VladSelfInvocationProbe selfInvocation;

    @BeforeEach
    void freshFixture() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS vm15_tx_events (
                    event_key VARCHAR(120) PRIMARY KEY,
                    note VARCHAR(120) NOT NULL
                )
                """);
        jdbc.update("DELETE FROM vm15_tx_events");
    }

    @Test
    void requiredParticipantMarksSharedTransactionRollbackOnlyEvenWhenCallerCatchesException() {
        assertThatThrownBy(() ->
                orchestration.caughtRequiredFailureStillRollsBack("outer-required", "inner-required"))
                .isInstanceOf(UnexpectedRollbackException.class);
        assertThat(keys()).isEmpty(); // Neither write can commit.
    }

    @Test
    void requiresNewCommitsIndependentlyEvenWhenOuterTransactionRollsBack() {
        assertThatThrownBy(() ->
                orchestration.outerRollbackDoesNotUndoRequiresNew("outer-fails", "inner-commits"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outer operation must roll back");
        assertThat(keys()).containsExactly("inner-commits");
    }

    @Test
    void publicTransactionalMethodCalledThroughSelfDoesNotPassSpringProxy() {
        assertThat(selfInvocation.internalCallBypassesTransactionalProxy()).isFalse();
        assertThat(selfInvocation.inspectedTransactionStatus()).isTrue();
    }

    @Test
    void nestedJdbcSavepointRollsBackOnlyItsOwnWork() {
        // A dedicated local manager is used here, rather than claiming that a
        // JpaTransactionManager supports arbitrary JPA-level nested transactions.
        var manager = new DataSourceTransactionManager(dataSource);
        var outer = new TransactionTemplate(manager);
        var nested = new TransactionTemplate(manager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        outer.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO vm15_tx_events VALUES (?, ?)", "outer-savepoint", "outer");
            nested.executeWithoutResult(innerStatus -> {
                jdbc.update("INSERT INTO vm15_tx_events VALUES (?, ?)", "nested-rolled-back", "nested");
                innerStatus.setRollbackOnly(); // rollback to the JDBC savepoint, not entire tx
            });
            jdbc.update("INSERT INTO vm15_tx_events VALUES (?, ?)", "after-savepoint", "outer");
        });
        assertThat(keys()).containsExactly("after-savepoint", "outer-savepoint");
    }

    @Test
    void checkedExceptionDoesNotRollbackUnderSpringDefaultPolicy() {
        assertThatThrownBy(() ->
                inner.checkedExceptionWithDefaultPolicy("checked-commits"))
                .isInstanceOf(IOException.class);
        assertThat(keys()).containsExactly("checked-commits");
    }

    @Test
    void explicitRollbackForMakesCheckedExceptionRollBack() {
        assertThatThrownBy(() ->
                inner.checkedExceptionWithExplicitRollback("checked-rolls-back"))
                .isInstanceOf(IOException.class);
        assertThat(keys()).isEmpty();
    }

    private List<String> keys() {
        return jdbc.queryForList(
                "SELECT event_key FROM vm15_tx_events ORDER BY event_key", String.class);
    }
}
