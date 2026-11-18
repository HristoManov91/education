package bg.hristomanov.education.tx;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * Forward-only JDBC consumption with bounded application buffering.
 * For pgJDBC, a positive fetchSize and autoCommit=false allow cursor retrieval.
 * This is not a cross-driver memory or latency guarantee.
 */
public final class JdbcStreamingReader {
    private final DataSource source;

    public JdbcStreamingReader(DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public record ScanSummary(long rows, long checksum) {}

    public ScanSummary scanIds(int fetchSize, LongConsumer consume) throws SQLException {
        if (fetchSize <= 0) throw new IllegalArgumentException("fetchSize must be positive");
        Objects.requireNonNull(consume);
        try (Connection connection = source.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                long rows = 0, checksum = 0;
                try (var statement = connection.prepareStatement(
                        "SELECT id FROM stream_event ORDER BY id",
                        java.sql.ResultSet.TYPE_FORWARD_ONLY,
                        java.sql.ResultSet.CONCUR_READ_ONLY)) {
                    statement.setFetchSize(fetchSize);
                    try (var rs = statement.executeQuery()) {
                        while (rs.next()) {
                            long id = rs.getLong(1);
                            consume.accept(id);
                            rows++;
                            checksum += id;
                        }
                    }
                }
                connection.commit();
                return new ScanSummary(rows, checksum);
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                throw e;
            }
        }
    }
}
