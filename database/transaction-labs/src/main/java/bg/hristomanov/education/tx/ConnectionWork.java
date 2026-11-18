package bg.hristomanov.education.tx;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Resource ownership demonstration. Transaction boundaries and remote IO live outside.
 * No injected EntityManager or Spring proxy hides the physical JDBC lease.
 */
public final class ConnectionWork {

    private final DataSource dataSource;

    public ConnectionWork(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public int loadQuantity(long id) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT quantity FROM account_balance WHERE id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Missing account " + id);
                }
                return rows.getInt(1);
            }
        }
    }

    public int updateIfVersion(long id, int newQuantity, int expectedVersion) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "UPDATE account_balance SET quantity=?, version=version+1 "
                     + "WHERE id=? AND version=?")) {
            statement.setInt(1, newQuantity);
            statement.setLong(2, id);
            statement.setInt(3, expectedVersion);
            return statement.executeUpdate(); // 0 => optimistic conflict, do not silently ignore.
        }
    }
}
