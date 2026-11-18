package bg.hristomanov.education.tx;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostgresFetchingLabIT {
    @Container static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    private static PGSimpleDataSource db;

    @BeforeAll static void seed() throws SQLException {
        db = new PGSimpleDataSource();
        db.setURL(POSTGRES.getJdbcUrl());
        db.setUser(POSTGRES.getUsername());
        db.setPassword(POSTGRES.getPassword());
        try (Connection c = db.getConnection(); var stmt = c.createStatement()) {
            stmt.execute("CREATE TABLE stream_event(id BIGINT PRIMARY KEY, payload TEXT NOT NULL)");
            stmt.execute("INSERT INTO stream_event SELECT n, repeat('x',100) FROM generate_series(1,105) n");
        }
    }

    @Test void multipleFetchSizesReturnIdenticalRows() throws SQLException {
        var reader = new JdbcStreamingReader(db);
        var seen = new AtomicInteger();
        var first = reader.scanIds(1, id -> seen.incrementAndGet());
        var second = reader.scanIds(10, id -> {});
        assertThat(first).isEqualTo(second);
        assertThat(first.rows()).isEqualTo(105);
        assertThat(first.checksum()).isEqualTo(5565);
        assertThat(seen).hasValue(105);
    }

    @Test void consumerFailureRollsBackAndClosesJdbcResources() throws SQLException {
        var reader = new JdbcStreamingReader(db);
        var seen = new AtomicInteger();
        assertThatThrownBy(() -> reader.scanIds(8, id -> {
            if (seen.incrementAndGet() == 12) throw new IllegalStateException("stop");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(seen).hasValue(12);
        assertThat(reader.scanIds(25, id -> {}).rows()).isEqualTo(105);
    }

    @Test void rejectsNonpositiveFetchSize() {
        assertThatThrownBy(() -> new JdbcStreamingReader(db).scanIds(0, id -> {}))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
