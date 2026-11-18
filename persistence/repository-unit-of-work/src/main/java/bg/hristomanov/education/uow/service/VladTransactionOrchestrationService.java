package bg.hristomanov.education.uow.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The orchestration and inner participant are separate Spring beans on purpose:
 * this ensures that proxy-based TransactionInterceptor sees the inner call.
 */
@Service
public class VladTransactionOrchestrationService {
    private final JdbcTemplate jdbc;
    private final VladTransactionInnerService inner;

    public VladTransactionOrchestrationService(
            JdbcTemplate jdbc,
            VladTransactionInnerService inner) {
        this.jdbc = jdbc;
        this.inner = inner;
    }

    @Transactional
    public void caughtRequiredFailureStillRollsBack(String outerKey, String innerKey) {
        jdbc.update("INSERT INTO vm15_tx_events(event_key, note) VALUES (?, ?)",
                outerKey, "outer REQUIRED");
        try {
            inner.insertThenFailParticipating(innerKey);
        } catch (IllegalStateException expected) {
            // Catching the Java exception cannot clear the transaction's rollback-only marker.
        }
        // At outer commit, Spring raises UnexpectedRollbackException.
    }

    @Transactional
    public void outerRollbackDoesNotUndoRequiresNew(String outerKey, String innerKey) {
        jdbc.update("INSERT INTO vm15_tx_events(event_key, note) VALUES (?, ?)",
                outerKey, "outer rollback");
        inner.insertIndependent(innerKey);
        throw new IllegalStateException("Outer operation must roll back");
    }
}
