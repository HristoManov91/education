package bg.hristomanov.education.sql;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class H2SqlSemanticsTest {

    private DataSource dataSource;
    private SqlReadRepository repository;

    @BeforeEach
    void freshSqlFixture() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:vm_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource = db;
        repository = new SqlReadRepository(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            SqlScriptRunner.run(connection, "/sql/postgresql/schema.sql");
            SqlScriptRunner.run(connection, "/sql/postgresql/seed.sql");
        }
    }

    @Test
    void nullEqualityIsUnknownAndCountColumnSkipsNulls() throws SQLException {
        assertThat(scalar("SELECT COUNT(*) FROM customer WHERE manager_id = NULL")).isZero();
        assertThat(scalar("SELECT COUNT(*) FROM customer WHERE manager_id IS NULL")).isEqualTo(2);
        assertThat(scalar("SELECT COUNT(*) FROM customer")).isEqualTo(5);
        assertThat(scalar("SELECT COUNT(manager_id) FROM customer")).isEqualTo(3);
    }

    @Test
    void leftJoinOnPreservesUnmatchedCustomersButWhereFiltersThem() throws SQLException {
        String on = """
                SELECT COUNT(DISTINCT c.id)
                FROM customer c LEFT JOIN purchase_order o
                    ON o.customer_id = c.id AND o.status = 'PAID'
                """;
        String where = """
                SELECT COUNT(DISTINCT c.id)
                FROM customer c LEFT JOIN purchase_order o ON o.customer_id = c.id
                WHERE o.status = 'PAID'
                """;
        assertThat(scalar(on)).isEqualTo(5);
        assertThat(scalar(where)).isEqualTo(3);
    }

    @Test
    void existsExpressesParentFilteringWithoutMultiplyingRows() throws SQLException {
        long naiveJoinRows = scalar("""
                SELECT COUNT(*) FROM customer c
                JOIN purchase_order o ON o.customer_id = c.id
                WHERE o.status = 'PAID'
                """);
        assertThat(naiveJoinRows).isEqualTo(4);
        assertThat(repository.customerIdsWithPaidOrders()).containsExactly(1L, 2L, 3L);
        assertThat(repository.customerIdsWithoutPaidOrders()).containsExactly(4L, 5L);
    }

    @Test
    void databaseConstraintsRejectInvalidStateNotJustJavaValidation() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO customer(id, email, full_name, region)
                VALUES (6, 'ada@example.test', 'Duplicate', 'BG')
                """)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO purchase_order(id, customer_id, external_ref, status, total, placed_at)
                VALUES (108, 999, 'ORPHAN', 'PAID', 1.00, CURRENT_TIMESTAMP)
                """)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO purchase_order(id, customer_id, external_ref, status, total, placed_at)
                VALUES (109, 1, 'A-01', 'PAID', 1, CURRENT_TIMESTAMP)
                """)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO purchase_order(id, customer_id, external_ref, status, total, placed_at)
                VALUES (110, 1, 'WRONG-STATUS', 'WRONG', 1, CURRENT_TIMESTAMP)
                """)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO purchase_order(id, customer_id, external_ref, status, total, placed_at)
                VALUES (111, 1, 'NEGATIVE-TOTAL', 'PAID', -1, CURRENT_TIMESTAMP)
                """)).isInstanceOf(SQLException.class);
    }

    @Test
    void windowFunctionGetsTopPaidCustomerInEachRegion() throws SQLException {
        assertThat(repository.topPaidCustomerPerRegion()).containsExactly(
                new SqlReadRepository.RegionLeader("BG", 2L, new BigDecimal("180.00")),
                new SqlReadRepository.RegionLeader("DE", 3L, new BigDecimal("90.00")));
    }

    @Test
    void recursiveCteTraversesRootAndDescendants() throws SQLException {
        assertThat(repository.descendantsOfPostgres(1)).containsExactly(
                new SqlReadRepository.Descendant(1L, 0),
                new SqlReadRepository.Descendant(2L, 1),
                new SqlReadRepository.Descendant(5L, 1),
                new SqlReadRepository.Descendant(4L, 2));
        assertThat(repository.descendantsOfPostgres(999)).isEqualTo(List.of());
    }

    private long scalar(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var results = statement.executeQuery(sql)) {
            results.next();
            return results.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
