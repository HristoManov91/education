package bg.hristomanov.education.sql;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read models deliberately use JDBC rather than JPA to expose SQL and row cardinality.
 * The caller provides a DataSource; this class does not own the connection pool.
 */
public final class SqlReadRepository {
    public record RegionLeader(String region, long customerId, BigDecimal paidTotal) {}
    public record Descendant(long customerId, int depth) {}

    private static final String WITH_PAID_ORDERS = """
            SELECT c.id
            FROM customer c
            WHERE EXISTS (
                SELECT 1 FROM purchase_order o
                WHERE o.customer_id = c.id AND o.status = 'PAID'
            )
            ORDER BY c.id
            """;

    private static final String WITHOUT_PAID_ORDERS = """
            SELECT c.id
            FROM customer c
            WHERE NOT EXISTS (
                SELECT 1 FROM purchase_order o
                WHERE o.customer_id = c.id AND o.status = 'PAID'
            )
            ORDER BY c.id
            """;

    // LEFT JOIN includes customers with no purchases. CASE is portable to Oracle 19c.
    private static final String TOP_CUSTOMER_PER_REGION = """
            WITH revenue AS (
                SELECT c.id AS customer_id, c.region,
                       COALESCE(SUM(CASE WHEN o.status = 'PAID' THEN o.total END), 0) AS paid_total
                FROM customer c
                LEFT JOIN purchase_order o ON o.customer_id = c.id
                GROUP BY c.id, c.region
            ), ranked AS (
                SELECT region, customer_id, paid_total,
                       ROW_NUMBER() OVER (
                           PARTITION BY region
                           ORDER BY paid_total DESC, customer_id ASC
                       ) AS row_position
                FROM revenue
            )
            SELECT region, customer_id, paid_total
            FROM ranked
            WHERE row_position = 1
            ORDER BY region
            """;

    // PostgreSQL/H2 syntax. Oracle recursive subquery factoring omits RECURSIVE.
    private static final String DESCENDANTS_POSTGRESQL = """
            WITH RECURSIVE hierarchy(id, depth) AS (
                SELECT id, 0 FROM customer WHERE id = ?
                UNION ALL
                SELECT c.id, h.depth + 1
                FROM customer c JOIN hierarchy h ON c.manager_id = h.id
                WHERE h.depth < 100
            )
            SELECT id, depth FROM hierarchy ORDER BY depth, id
            """;

    private final DataSource dataSource;

    public SqlReadRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public List<Long> customerIdsWithPaidOrders() throws SQLException {
        return select(WITH_PAID_ORDERS, rs -> rs.getLong(1));
    }

    public List<Long> customerIdsWithoutPaidOrders() throws SQLException {
        return select(WITHOUT_PAID_ORDERS, rs -> rs.getLong(1));
    }

    public List<RegionLeader> topPaidCustomerPerRegion() throws SQLException {
        return select(TOP_CUSTOMER_PER_REGION, rs -> new RegionLeader(
                rs.getString("region"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("paid_total")));
    }

    public List<Descendant> descendantsOfPostgres(long rootCustomerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DESCENDANTS_POSTGRESQL)) {
            // Bind values; never interpolate user input into SQL text.
            statement.setLong(1, rootCustomerId);
            try (ResultSet results = statement.executeQuery()) {
                List<Descendant> descendants = new ArrayList<>();
                while (results.next()) {
                    descendants.add(new Descendant(results.getLong(1), results.getInt(2)));
                }
                return descendants;
            }
        }
    }

    private <T> List<T> select(String sql, RowMapper<T> mapper) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (results.next()) {
                rows.add(mapper.map(results));
            }
            return rows;
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
