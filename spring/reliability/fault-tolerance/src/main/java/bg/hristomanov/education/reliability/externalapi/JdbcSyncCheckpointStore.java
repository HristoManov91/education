package bg.hristomanov.education.reliability.externalapi;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * За учебния lab използваме чист JDBC, за да се вижда атомарността.
 *
 * Schema (използваме VARCHAR/BOOLEAN, съвместими с H2/PostgreSQL):
 * sync_checkpoint(job_id VARCHAR(80) PRIMARY KEY, next_page INTEGER NOT NULL,
 *                 complete BOOLEAN NOT NULL)
 * sync_item(job_id VARCHAR(80), external_id VARCHAR(80), content VARCHAR(100),
 *           PRIMARY KEY(job_id, external_id))
 *
 * Предварително регистрираме job чрез createJob() извън конкурентния worker path.
 * В production lifecycle-ът на job-овете включва unique creation, ownership,
 * lease/heartbeats и snapshot/cursor semantics.
 */
public final class JdbcSyncCheckpointStore implements ResumableSync.PageStore {

    private final DataSource dataSource;

    public JdbcSyncCheckpointStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void createJob(String jobId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sync_checkpoint(job_id, next_page, complete) VALUES (?, 1, FALSE)")) {
                statement.setString(1, jobId);
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot create sync job", failure);
        }
    }

    @Override
    public ResumableSync.Progress progress(String jobId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT next_page, complete FROM sync_checkpoint WHERE job_id = ?")) {
            statement.setString(1, jobId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Unknown sync job: " + jobId);
                }
                return new ResumableSync.Progress(rows.getInt(1), rows.getBoolean(2));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot read sync checkpoint", failure);
        }
    }

    @Override
    public void commitPage(String jobId, int expectedPage, ResumableSync.Page page) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Row lock сериализира конкурентните workers за един job.
                ResumableSync.Progress stored = lockCheckpoint(connection, jobId);
                if (stored.complete() || stored.nextPage() != expectedPage) {
                    throw new IllegalStateException("Stale or completed sync page: " + expectedPage);
                }

                for (ResumableSync.Item item : page.items()) {
                    // UNIQUE(job_id, external_id) = idempotent item identity.
                    int updated;
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE sync_item SET content = ? WHERE job_id = ? AND external_id = ?")) {
                        update.setString(1, item.content());
                        update.setString(2, jobId);
                        update.setString(3, item.externalId());
                        updated = update.executeUpdate();
                    }
                    if (updated == 0) {
                        try (PreparedStatement insert = connection.prepareStatement(
                                "INSERT INTO sync_item(job_id, external_id, content) VALUES (?, ?, ?)")) {
                            insert.setString(1, jobId);
                            insert.setString(2, item.externalId());
                            insert.setString(3, item.content());
                            insert.executeUpdate();
                        }
                    }
                }

                try (PreparedStatement checkpoint = connection.prepareStatement(
                        "UPDATE sync_checkpoint SET next_page = ?, complete = ? WHERE job_id = ?")) {
                    checkpoint.setInt(1, Math.addExact(expectedPage, 1));
                    checkpoint.setBoolean(2, !page.hasMore());
                    checkpoint.setString(3, jobId);
                    if (checkpoint.executeUpdate() != 1) {
                        throw new IllegalStateException("Checkpoint missing after lock");
                    }
                }
                // Items + checkpoint commit заедно. Няма „записано, но прескочено“.
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Atomic page commit failed", failure);
        }
    }

    private ResumableSync.Progress lockCheckpoint(Connection connection, String jobId)
            throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT next_page, complete FROM sync_checkpoint WHERE job_id = ? FOR UPDATE")) {
            lock.setString(1, jobId);
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Unknown sync job: " + jobId);
                }
                return new ResumableSync.Progress(rows.getInt(1), rows.getBoolean(2));
            }
        }
    }
}
