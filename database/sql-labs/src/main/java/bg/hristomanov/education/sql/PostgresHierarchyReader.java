package bg.hristomanov.education.sql;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL-only cycle-safe recursive CTE over a parent/child table.
 * Array-path detection is a correctness policy, not a claim about DB portability.
 */
public final class PostgresHierarchyReader {
    public record Descendant(long id, int depth) {}

    private final DataSource source;

    public PostgresHierarchyReader(DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public List<Descendant> descendantsOf(long rootId, int maximumDepth) throws SQLException {
        if (maximumDepth < 0 || maximumDepth > 1000) {
            throw new IllegalArgumentException("maximumDepth must be within 0..1000");
        }
        String sql = """
                WITH RECURSIVE walk(id, depth, visited) AS (
                  SELECT id, 0, ARRAY[id] FROM vm29_nodes WHERE id = ?
                  UNION ALL
                  SELECT child.id, parent.depth + 1, parent.visited || child.id
                  FROM vm29_nodes child
                  JOIN walk parent ON child.parent_id = parent.id
                  WHERE parent.depth < ?
                    AND NOT (child.id = ANY(parent.visited))
                )
                SELECT id, depth FROM walk ORDER BY depth, id
                """;
        try (var connection = source.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, rootId);
            statement.setInt(2, maximumDepth);
            try (var rs = statement.executeQuery()) {
                List<Descendant> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new Descendant(rs.getLong(1), rs.getInt(2)));
                }
                return List.copyOf(rows);
            }
        }
    }
}
