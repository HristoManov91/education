package bg.hristomanov.education.reliability.externalapi;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 проверява истински ACID rollback и SELECT FOR UPDATE, не само fake store.
 * Production PostgreSQL/Oracle изискват и отделни SQL dialect/concurrency tests.
 */
class ResumableSyncTest {

    private DataSource dataSource;
    private JdbcSyncCheckpointStore store;
    private ResumableSync runner;

    @BeforeEach
    void setupSchema() throws SQLException {
        JdbcDataSource jdbc = new JdbcDataSource();
        jdbc.setURL("jdbc:h2:mem:page_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        jdbc.setUser("sa");
        dataSource = jdbc;
        try (Connection connection = jdbc.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE sync_checkpoint (
                    job_id VARCHAR(80) PRIMARY KEY,
                    next_page INTEGER NOT NULL,
                    complete BOOLEAN NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE sync_item (
                    job_id VARCHAR(80) NOT NULL,
                    external_id VARCHAR(80) NOT NULL,
                    content VARCHAR(100) NOT NULL,
                    PRIMARY KEY (job_id, external_id)
                )
                """);
        }
        store = new JdbcSyncCheckpointStore(dataSource);
        runner = new ResumableSync(store);
    }

    @Test
    void failureOnPage47ResumesAt47RatherThanRefetchingCommittedPages() throws SQLException {
        store.createJob("job-a");
        List<Integer> firstAttempts = new ArrayList<>();

        assertThatThrownBy(() -> runner.run("job-a", page -> {
            firstAttempts.add(page);
            if (page == 47) {
                throw new IllegalStateException("Remote API returned 429");
            }
            return new ResumableSync.Page(
                    List.of(new ResumableSync.Item("id-" + page, "value-" + page)),
                    page < 50);
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("429");

        assertThat(store.progress("job-a").nextPage()).isEqualTo(47);
        assertThat(store.progress("job-a").complete()).isFalse();
        assertThat(recordCount("job-a")).isEqualTo(46);

        List<Integer> resumedAttempts = new ArrayList<>();
        runner.run("job-a", page -> {
            resumedAttempts.add(page);
            return new ResumableSync.Page(
                    List.of(new ResumableSync.Item("id-" + page, "value-" + page)),
                    page < 50);
        });

        assertThat(resumedAttempts).containsExactly(47, 48, 49, 50);
        assertThat(store.progress("job-a").complete()).isTrue();
        assertThat(recordCount("job-a")).isEqualTo(50);
        runner.run("job-a", page -> {
            throw new AssertionError("Completed job must not call remote");
        });
    }

    @Test
    void invalidSecondRecordRollsBackEarlierRowsAndCheckpointAtomically()
            throws SQLException {
        store.createJob("job-b");

        ResumableSync.Page invalid = new ResumableSync.Page(List.of(
                new ResumableSync.Item("ok", "first row"),
                new ResumableSync.Item("too-long", "x".repeat(250))
        ), true);

        assertThatThrownBy(() -> store.commitPage("job-b", 1, invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Atomic page commit");
        assertThat(recordCount("job-b")).isZero();
        assertThat(store.progress("job-b").nextPage()).isEqualTo(1);

        store.commitPage("job-b", 1, new ResumableSync.Page(
                List.of(new ResumableSync.Item("ok", "good")), false));
        assertThat(recordCount("job-b")).isEqualTo(1);
        assertThat(store.progress("job-b").complete()).isTrue();
    }

    @Test
    void competingWorkerCannotCommitSamePageTwice() throws SQLException {
        store.createJob("job-c");
        ResumableSync.Page page = new ResumableSync.Page(
                List.of(new ResumableSync.Item("id", "value")), true);
        store.commitPage("job-c", 1, page);
        assertThatThrownBy(() -> store.commitPage("job-c", 1, page))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stale");
        assertThat(store.progress("job-c").nextPage()).isEqualTo(2);
        assertThat(recordCount("job-c")).isEqualTo(1);
    }

    @Test
    void rerenderedItemFromLaterPageUpdatesInsteadOfDuplicating() throws SQLException {
        store.createJob("job-d");
        store.commitPage("job-d", 1, new ResumableSync.Page(
                List.of(new ResumableSync.Item("shared-id", "first version")), true));
        store.commitPage("job-d", 2, new ResumableSync.Page(
                List.of(new ResumableSync.Item("shared-id", "new version")), false));

        assertThat(recordCount("job-d")).isEqualTo(1);
        assertThat(itemValue("job-d", "shared-id")).isEqualTo("new version");
    }

    private int recordCount(String jobId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM sync_item WHERE job_id = ?")) {
            statement.setString(1, jobId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private String itemValue(String jobId, String itemId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT content FROM sync_item WHERE job_id = ? AND external_id = ?")) {
            statement.setString(1, jobId);
            statement.setString(2, itemId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }
}
