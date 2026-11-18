package bg.hristomanov.education.sql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Collectors;

/**
 * Fixture-only runner: no quoted semicolons, functions or procedural SQL in these files.
 * Real migration scripts should use Flyway/Liquibase rather than this tiny helper.
 */
final class SqlScriptRunner {
    private SqlScriptRunner() {}

    static void run(Connection connection, String resourcePath) throws SQLException, IOException {
        try (var stream = SqlScriptRunner.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing SQL fixture: " + resourcePath);
            }
            String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String script = raw.lines()
                    .map(line -> line.replaceFirst("--.*$", ""))
                    .collect(Collectors.joining("\n"));
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
        }
    }
}
