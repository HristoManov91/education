package bg.hristomanov.education.uow.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Transaction advice is applied on external calls through the Spring proxy.
 * Calls from another service make REQUIRED/REQUIRES_NEW behavior observable.
 */
@Service
public class VladTransactionInnerService {

    private final JdbcTemplate jdbc;

    public VladTransactionInnerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void insertThenFailParticipating(String key) {
        insert(key, "participating REQUIRED");
        // TransactionInterceptor sees the unchecked exception and marks
        // the shared physical transaction rollback-only.
        throw new IllegalStateException("Participating business operation failed");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertIndependent(String key) {
        // The outer transaction is suspended while this independent one commits.
        insert(key, "independent REQUIRES_NEW");
    }

    @Transactional
    public void checkedExceptionWithDefaultPolicy(String key) throws IOException {
        insert(key, "checked default");
        throw new IOException("Checked business failure");
    }

    @Transactional(rollbackFor = IOException.class)
    public void checkedExceptionWithExplicitRollback(String key) throws IOException {
        insert(key, "checked rollbackFor");
        throw new IOException("Checked failure requiring rollback");
    }

    private void insert(String key, String description) {
        jdbc.update("INSERT INTO vm15_tx_events(event_key, note) VALUES (?, ?)", key, description);
    }
}
