package bg.hristomanov.education.sql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.sql.SQLException;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostgresHierarchyAnalyticsIT {
    @Container static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    private static PGSimpleDataSource source;
    private static PostgresHierarchyReader reader;

    @BeforeAll static void schema() throws SQLException {
        source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        reader = new PostgresHierarchyReader(source);
        try (var c = source.getConnection(); var st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE vm29_nodes (
                      id BIGINT PRIMARY KEY,
                      parent_id BIGINT REFERENCES vm29_nodes(id),
                      label TEXT NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE vm29_scores (
                      team TEXT NOT NULL, worker_id BIGINT NOT NULL, score INT NOT NULL,
                      PRIMARY KEY(team,worker_id)
                    )
                    """);
        }
    }

    @BeforeEach void seed() throws SQLException {
        try (var c = source.getConnection(); var st = c.createStatement()) {
            st.execute("TRUNCATE vm29_nodes, vm29_scores");
            st.execute("""
                    INSERT INTO vm29_nodes VALUES
                    (1,NULL,'root'),(2,1,'left'),(3,2,'leaf'),(4,1,'right'),(5,NULL,'other')
                    """);
            st.execute("""
                    INSERT INTO vm29_scores VALUES
                    ('BG',10,100),('BG',11,100),('BG',12,80),
                    ('DE',20,90),('DE',21,40)
                    """);
        }
    }

    @Test void recursiveCteReturnsDeterministicDepthOrderedDescendants() throws SQLException {
        assertThat(reader.descendantsOf(1, 10)).containsExactly(
                new PostgresHierarchyReader.Descendant(1, 0),
                new PostgresHierarchyReader.Descendant(2, 1),
                new PostgresHierarchyReader.Descendant(4, 1),
                new PostgresHierarchyReader.Descendant(3, 2));
        assertThat(reader.descendantsOf(1, 1)).hasSize(3);
        assertThat(reader.descendantsOf(999, 10)).isEmpty();
    }

    @Test void cycleGuardPreventsRevisitingRootWhenManagersFormCycle() throws SQLException {
        try (var c = source.getConnection(); var st = c.createStatement()) {
            st.execute("UPDATE vm29_nodes SET parent_id = 3 WHERE id = 1");
        }
        assertThat(reader.descendantsOf(1, 100)).hasSize(4)
                .extracting(PostgresHierarchyReader.Descendant::id)
                .containsExactly(1L, 2L, 4L, 3L);
        assertThatThrownBy(() -> reader.descendantsOf(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rankPreservesTiesWhileRowNumberPicksOneDeterministicWinner() throws SQLException {
        var ties = new ArrayList<Long>();
        var winner = new ArrayList<Long>();
        try (var c = source.getConnection();
             var st = c.createStatement();
             var rs = st.executeQuery("""
                     SELECT worker_id, RANK() OVER (
                        PARTITION BY team ORDER BY score DESC
                     ) AS position_rank,
                     ROW_NUMBER() OVER (
                        PARTITION BY team ORDER BY score DESC,worker_id
                     ) AS position_row
                     FROM vm29_scores WHERE team='BG'
                     ORDER BY worker_id
                     """)) {
            while (rs.next()) {
                if (rs.getInt("position_rank") == 1) ties.add(rs.getLong(1));
                if (rs.getInt("position_row") == 1) winner.add(rs.getLong(1));
            }
        }
        assertThat(ties).containsExactly(10L, 11L);
        assertThat(winner).containsExactly(10L);
    }
}
