package bg.hristomanov.education.hperf;

import bg.hristomanov.education.hperf.domain.DocumentMetadata;
import bg.hristomanov.education.hperf.domain.DocumentState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PostgreSQL-specific round-trip proof: H2 is not a JSONB/ARRAY substitute. */
@SpringBootTest
@Testcontainers
class PostgresAdvancedTypesIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void jsonbEnumAndInstantRoundTripIncludingDirtyUpdate() {
        String reference = "VM22-" + UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-11-19T14:30:00Z");

        Long id = transactions.execute(status -> {
            var doc = new DocumentMetadata(reference, DocumentState.DRAFT,
                    Map.of("channel", "WEB", "risk", 7), timestamp);
            entityManager.persist(doc);
            entityManager.flush();
            return doc.getId();
        });

        String physicalType = jdbc.queryForObject(
                "SELECT pg_typeof(attributes)::text FROM vm22_document_metadata WHERE id = ?",
                String.class, id);
        assertThat(physicalType).isEqualTo("jsonb");

        String channel = jdbc.queryForObject(
                "SELECT attributes ->> 'channel' FROM vm22_document_metadata WHERE id = ?",
                String.class, id);
        assertThat(channel).isEqualTo("WEB");

        transactions.executeWithoutResult(status -> {
            var managed = entityManager.find(DocumentMetadata.class, id);
            assertThat(managed.getCreatedAt()).isEqualTo(timestamp);
            assertThat(managed.getState()).isEqualTo(DocumentState.DRAFT);
            assertThat(managed.getAttributes()).containsEntry("risk", 7);
            managed.replaceAttributes(Map.of("channel", "MOBILE", "risk", 9));
            managed.changeState(DocumentState.REVIEWED);
        });

        transactions.executeWithoutResult(status -> {
            entityManager.clear(); // a new SQL load, not just the identity map
            var reloaded = entityManager.find(DocumentMetadata.class, id);
            assertThat(reloaded.getState()).isEqualTo(DocumentState.REVIEWED);
            assertThat(reloaded.getAttributes()).containsEntry("channel", "MOBILE");
            assertThat(reloaded.getAttributes()).containsEntry("risk", 9);
            assertThat(reloaded.getCreatedAt()).isEqualTo(timestamp);
        });
    }

    @Test
    void jsonNullAndSqlNullAreDifferentValues() {
        String result = jdbc.queryForObject(
                "SELECT CASE WHEN 'null'::jsonb = 'null'::jsonb AND " +
                "'null'::jsonb IS NOT NULL THEN 'distinct' ELSE 'unexpected' END",
                String.class);
        assertThat(result).isEqualTo("distinct");
    }
}
