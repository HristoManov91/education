package bg.hristomanov.education.consistency;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * Учебен single-consumer, single-worker модел с реални JDBC транзакции.
 * Външният broker потвърждава доставка едва след успешен receive().
 * Не изпълняваме remote I/O под DB locks.
 */
public final class DurableInbox {
    public enum Status { PENDING, DONE, FAILED }
    public enum Receipt { STORED, DUPLICATE }
    public enum Result { APPLIED, ALREADY_DONE, NOT_DUE, GAP, RETRY_SCHEDULED, TERMINAL_FAILURE }
    public record Event(String id, String aggregateId, long sequence, long observedAtMillis, String payload) {
        public Event {
            Objects.requireNonNull(id); Objects.requireNonNull(aggregateId); Objects.requireNonNull(payload);
            if (id.isBlank() || aggregateId.isBlank() || sequence < 1) throw new IllegalArgumentException("Invalid event");
        }
    }
    public record Row(Event event, Status status, int attempts, long dueAt, String lastError,
                      int redrives, String redriveReason) {}
    public record Account(long balance, long lastSequence) {}
    public static final class RetryableFailure extends RuntimeException {
        public RetryableFailure(String message) { super(message); }
    }
    private static final class PermanentFailure extends RuntimeException {
        PermanentFailure(String message) { super(message); }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final int maxAttempts;
    private final long backoffMillis;

    public DurableInbox(DataSource dataSource, int maxAttempts, long backoffMillis) {
        if (maxAttempts < 1 || backoffMillis < 1) throw new IllegalArgumentException("Positive retry budget required");
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        // Изрични независими граници; caller не трябва да ACK-ва вътре в чужда TX.
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    public void initializeSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS account_state (
                  aggregate_id VARCHAR(100) PRIMARY KEY,
                  balance BIGINT NOT NULL CHECK (balance >= 0),
                  last_sequence BIGINT NOT NULL CHECK (last_sequence >= 0)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS durable_inbox (
                  event_id VARCHAR(100) PRIMARY KEY,
                  aggregate_id VARCHAR(100) NOT NULL REFERENCES account_state(aggregate_id),
                  seq_no BIGINT NOT NULL CHECK (seq_no > 0),
                  observed_at BIGINT NOT NULL,
                  payload VARCHAR(4000) NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  attempts INTEGER NOT NULL,
                  due_at BIGINT NOT NULL,
                  last_error VARCHAR(500),
                  redrives INTEGER NOT NULL,
                  redrive_reason VARCHAR(500),
                  UNIQUE (aggregate_id, seq_no)
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS inbox_due ON durable_inbox(status, due_at, aggregate_id, seq_no)");
    }

    public void register(String aggregateId, long balance) {
        jdbc.update("INSERT INTO account_state VALUES (?, ?, 0)", aggregateId, balance);
    }

    public Receipt receive(Event event, long now) {
        return receive(event, now, () -> {});
    }

    // Failure injection point for tests, before the receipt commit.
    Receipt receive(Event event, long now, Runnable beforeCommit) {
        Objects.requireNonNull(event);
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                jdbc.update("""
                        INSERT INTO durable_inbox
                        (event_id, aggregate_id, seq_no, observed_at, payload, status, attempts, due_at, redrives)
                        VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, 0)
                        """, event.id(), event.aggregateId(), event.sequence(), event.observedAtMillis(), event.payload(), now);
                beforeCommit.run();
                return Receipt.STORED;
            }));
        } catch (DuplicateKeyException duplicate) {
            // Предишната TX е приключила с rollback. Само идентичен envelope е safe duplicate.
            var existing = find(event.id(), false);
            if (!existing.isEmpty() && existing.getFirst().event().equals(event)) return Receipt.DUPLICATE;
            throw new IllegalStateException("Conflicting event ID or aggregate sequence: " + event.id(), duplicate);
        }
    }

    public List<String> dueIds(long now, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Bounded batch required");
        return jdbc.queryForList("""
                SELECT event_id FROM durable_inbox WHERE status = 'PENDING' AND due_at <= ?
                ORDER BY due_at, aggregate_id, seq_no FETCH FIRST ? ROWS ONLY
                """, String.class, now, limit);
    }

    public Result process(String id, long now) {
        return process(id, now, () -> {});
    }

    // Един worker на instance. Failure bookkeeping след rollback е отделна TX.
    // Не използвай този модел като multi-instance lease/claim protocol.
    synchronized Result process(String id, long now, Runnable afterEffect) {
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                var row = lockedRow(id);
                if (row.status() == Status.DONE) return Result.ALREADY_DONE;
                if (row.status() == Status.FAILED) return Result.TERMINAL_FAILURE;
                if (row.dueAt() > now) return Result.NOT_DUE;
                var account = account(row.event().aggregateId(), true);
                long expected = Math.addExact(account.lastSequence(), 1);
                if (row.event().sequence() > expected) {
                    jdbc.update("UPDATE durable_inbox SET due_at = ? WHERE event_id = ?",
                            Math.addExact(now, backoffMillis), id);
                    return Result.GAP; // gap не изразходва processing attempt
                }
                if (row.event().sequence() < expected) throw new PermanentFailure("Unexpected old sequence");
                long balance;
                try {
                    balance = Math.addExact(account.balance(), Long.parseLong(row.event().payload()));
                } catch (NumberFormatException | ArithmeticException invalid) {
                    throw new PermanentFailure("Invalid delta");
                }
                if (balance < 0) throw new PermanentFailure("Negative resulting balance");
                jdbc.update("UPDATE account_state SET balance = ?, last_sequence = ? WHERE aggregate_id = ?",
                        balance, row.event().sequence(), row.event().aggregateId());
                afterEffect.run(); // injected failure rolls back both balance and version
                jdbc.update("UPDATE durable_inbox SET status = 'DONE', attempts = attempts + 1 WHERE event_id = ?", id);
                return Result.APPLIED;
            }));
        } catch (PermanentFailure failure) {
            return recordFailure(id, now, true, failure.getMessage());
        } catch (RetryableFailure | TransientDataAccessException failure) {
            // Не записваме raw exception payload/SQL с чувствителна информация.
            return recordFailure(id, now, false, "Transient processing failure");
        }
    }

    private Result recordFailure(String id, long now, boolean permanent, String error) {
        return Objects.requireNonNull(tx.execute(status -> {
            var row = lockedRow(id);
            if (row.status() != Status.PENDING) throw new IllegalStateException("Worker ownership changed");
            int attempts = Math.addExact(row.attempts(), 1);
            boolean terminal = permanent || attempts >= maxAttempts;
            jdbc.update("UPDATE durable_inbox SET attempts = ?, status = ?, due_at = ?, last_error = ? WHERE event_id = ?",
                    attempts, terminal ? "FAILED" : "PENDING",
                    terminal ? now : Math.addExact(now, backoffMillis), error, id);
            return terminal ? Result.TERMINAL_FAILURE : Result.RETRY_SCHEDULED;
        }));
    }

    /** Trusted operator method in this lab, not a public HTTP endpoint. Same ID and immutable payload. */
    public synchronized boolean redrive(String id, long now, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("Auditable reason required");
        return Boolean.TRUE.equals(tx.execute(status ->
                jdbc.update("""
                        UPDATE durable_inbox SET status = 'PENDING', attempts = 0, due_at = ?,
                            redrives = redrives + 1, redrive_reason = ?
                        WHERE event_id = ? AND status = 'FAILED'
                        """, now, reason, id) == 1));
    }

    public Account account(String id) { return account(id, false); }
    private Account account(String id, boolean lock) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT balance,last_sequence FROM account_state WHERE aggregate_id = ?"
                + (lock ? " FOR UPDATE" : ""), (rs, n) -> new Account(rs.getLong(1), rs.getLong(2)), id));
    }
    public Row row(String id) { return requireRow(id, false); }
    public int count() { return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM durable_inbox", Integer.class)); }
    private Row lockedRow(String id) { return requireRow(id, true); }
    private Row requireRow(String id, boolean lock) {
        var rows = find(id, lock);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unknown event: " + id);
        return rows.getFirst();
    }
    private List<Row> find(String id, boolean lock) {
        return jdbc.query("SELECT * FROM durable_inbox WHERE event_id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Row(new Event(rs.getString("event_id"), rs.getString("aggregate_id"),
                        rs.getLong("seq_no"), rs.getLong("observed_at"), rs.getString("payload")),
                        Status.valueOf(rs.getString("status")), rs.getInt("attempts"), rs.getLong("due_at"),
                        rs.getString("last_error"), rs.getInt("redrives"), rs.getString("redrive_reason")), id);
    }
}
