package bg.hristomanov.education.sql;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresFlywayMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Test void versionedMigrationsAreOrderedIdempotentAndValidated() throws SQLException {
        String schema = "vm25_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema)
                .locations("classpath:vm25/sql")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        var ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());

        try (var c = ds.getConnection(); var stmt = c.createStatement()) {
            stmt.execute("INSERT INTO " + schema +
                    ".vm25_account(account_key,balance) VALUES('A-1',10)");
            try (var rs = stmt.executeQuery(
                    "SELECT active FROM " + schema + ".vm25_account WHERE account_key='A-1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isTrue();
            }
            try (var rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM " + schema +
                    ".flyway_schema_history WHERE success AND version IN ('1','2')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test void changingAppliedMigrationFailsChecksumValidation(@TempDir Path folder) throws Exception {
        String schema = "checksum_" + UUID.randomUUID().toString().replace("-", "");
        var migrationFile = folder.resolve("V1__create_demo.sql");
        Files.writeString(migrationFile, "CREATE TABLE vm25_checksum_record (id BIGINT PRIMARY KEY);");

        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema)
                .locations("filesystem:" + folder.toAbsolutePath())
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        Files.writeString(migrationFile, "CREATE TABLE vm25_checksum_record (id BIGINT PRIMARY KEY, changed INT);");

        var result = flyway.validateWithResult();
        assertThat(result.validationSuccessful).isFalse();
        assertThat(result.invalidMigrations).isNotEmpty();
    }
}
