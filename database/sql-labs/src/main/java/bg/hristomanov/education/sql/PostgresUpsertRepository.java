package bg.hristomanov.education.sql;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Objects;

/**
 * PostgreSQL-specific atomic upsert. Domain identity is (tenant_id, external_ref).
 * Never use check-then-insert as a concurrency guarantee.
 */
public final class PostgresUpsertRepository {

    public record LedgerEntry(long id, BigDecimal amount) {}

    private final DataSource datasource;

    public PostgresUpsertRepository(DataSource datasource) {
        this.datasource = Objects.requireNonNull(datasource);
    }

    public LedgerEntry put(long tenantId, String externalRef, BigDecimal amount) throws SQLException {
        Objects.requireNonNull(externalRef);
        Objects.requireNonNull(amount);
        try (var connection = datasource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO vm24_ledger(tenant_id, external_ref, amount)
                 VALUES(?, ?, ?)
                 ON CONFLICT (tenant_id, external_ref)
                 DO UPDATE SET amount = EXCLUDED.amount
                 RETURNING id, amount
                 """)) {
            statement.setLong(1, tenantId);
            statement.setString(2, externalRef);
            statement.setBigDecimal(3, amount);
            try (var row = statement.executeQuery()) {
                if (!row.next()) throw new SQLException("UPSERT returned no row");
                LedgerEntry result = new LedgerEntry(row.getLong(1), row.getBigDecimal(2));
                if (row.next()) throw new SQLException("UPSERT returned multiple rows");
                return result;
            }
        }
    }
}
