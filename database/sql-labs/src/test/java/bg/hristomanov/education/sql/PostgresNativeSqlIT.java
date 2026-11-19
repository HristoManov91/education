package bg.hristomanov.education.sql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Native SQL correctness and result mapping against real PostgreSQL 17. */
@Testcontainers
class PostgresNativeSqlIT {
    @Container static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    private static PGSimpleDataSource datasource;

    @BeforeAll static void setup() throws SQLException {
        datasource = new PGSimpleDataSource();
        datasource.setURL(POSTGRES.getJdbcUrl());
        datasource.setUser(POSTGRES.getUsername());
        datasource.setPassword(POSTGRES.getPassword());
        try (var c = datasource.getConnection(); var s = c.createStatement()) {
            s.execute("""
                CREATE TABLE vm23_invoice (
                  id BIGINT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
                  paid_at TIMESTAMPTZ NOT NULL
                )
                """);
            s.execute("""
                INSERT INTO vm23_invoice VALUES
                   (1,10,  5.00, '2026-11-19T12:00:00Z'),
                   (2,10, 10.00, '2026-11-19T13:00:00Z'),
                   (3,20,100.00, '2026-11-19T12:00:00Z')
                """);
            s.execute("""
                CREATE FUNCTION vm23_invoice_count(p_tenant BIGINT)
                RETURNS BIGINT LANGUAGE SQL STABLE AS
                $$ SELECT COUNT(*) FROM vm23_invoice WHERE tenant_id = p_tenant $$
                """);
        }
    }

    record InvoiceRow(long id, long tenantId, OffsetDateTime paidAt) {}

    @Test void parameterizedQueryMapsRowsWithoutCrossTenantLeak() throws SQLException {
        List<InvoiceRow> rows = new ArrayList<>();
        try (var c = datasource.getConnection();
             var stmt = c.prepareStatement("""
                 SELECT id, tenant_id, paid_at FROM vm23_invoice
                 WHERE tenant_id = ? ORDER BY paid_at, id
                 """)) {
            stmt.setLong(1, 10);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new InvoiceRow(rs.getLong(1), rs.getLong(2),
                            rs.getObject(3, OffsetDateTime.class)));
                }
            }
        }
        assertThat(rows).hasSize(2).extracting(InvoiceRow::id).containsExactly(1L,2L);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.tenantId()).isEqualTo(10);
            assertThat(row.paidAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        });
    }

    @Test void typedSqlFunctionUsesBindParameter() throws SQLException {
        try (var c = datasource.getConnection();
             var stmt = c.prepareStatement("SELECT vm23_invoice_count(?)")) {
            stmt.setLong(1,20);
            try (var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(1);
            }
        }
    }

    @Test void booleanArraySemanticsAreDatabaseSpecific() throws SQLException {
        try (var c = datasource.getConnection(); var stmt = c.createStatement();
             var rs = stmt.executeQuery(
                    "SELECT ARRAY[2,3]::int[] @> ARRAY[2]::int[]")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isTrue();
        }
    }

    @Test void violatingBusinessCheckStillFailsThroughNativeSql() {
        assertThatThrownBy(() -> {
            try (Connection c = datasource.getConnection();
                 var stmt = c.prepareStatement(
                    "INSERT INTO vm23_invoice(id,tenant_id,amount,paid_at) " +
                    "VALUES (4,10,?,CURRENT_TIMESTAMP)")) {
                stmt.setBigDecimal(1,new java.math.BigDecimal("-1"));
                stmt.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23514"));
    }
}
