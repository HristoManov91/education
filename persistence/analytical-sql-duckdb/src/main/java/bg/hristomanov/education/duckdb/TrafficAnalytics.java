package bg.hristomanov.education.duckdb;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Synthetic traffic: embedded DuckDB/JDBC + CSV/Parquet; no server, credentials
 * or production Oracle access. File locations are preapproved local inputs.
 */
public final class TrafficAnalytics {
    private TrafficAnalytics() {}

    public record HourBucket(LocalDateTime hour, long gpsPoints,
                             long distinctTrucks, long movingPoints) {}
    public record LatestPoint(String plate, LocalDateTime at,
                              int fiscalPoint, BigDecimal distanceKm, boolean moving) {}

    public static Connection inMemory() throws SQLException {
        return DriverManager.getConnection("jdbc:duckdb:");
    }

    private static String literal(Path file) {
        return "'" + file.toString().replace("'", "''") + "'";
    }

    private static String csvSource(Path file) throws IOException {
        Path actual = Objects.requireNonNull(file).toRealPath();
        if (!Files.isRegularFile(actual) || !actual.toString().endsWith(".csv")) {
            throw new IllegalArgumentException("Expected existing local .csv file");
        }
        // Identifiers MUST remain strings; do not rely on sniffer inference.
        return "read_csv(" + literal(actual) + ", header=true, auto_detect=false, delim=',', "
               + "columns={'event_time':'TIMESTAMP', 'plate':'VARCHAR', "
               + "'nearest_fp':'INTEGER', 'distance_km':'DECIMAL(9,3)', "
               + "'moving':'BOOLEAN', 'tracking_code':'VARCHAR'})";
    }

    private static String parquetSource(Path file) throws IOException {
        Path actual = Objects.requireNonNull(file).toRealPath();
        if (!Files.isRegularFile(actual) || !actual.toString().endsWith(".parquet")) {
            throw new IllegalArgumentException("Expected existing local .parquet file");
        }
        return "read_parquet(" + literal(actual) + ")";
    }

    public static List<HourBucket> hourlyCsv(Connection connection, Path csv)
            throws SQLException, IOException {
        String sql = """
                SELECT date_trunc('hour', event_time) AS bucket,
                       count(*) AS points, count(DISTINCT plate) AS trucks,
                       count(*) FILTER (WHERE moving) AS moving_points
                FROM %s GROUP BY 1 ORDER BY 1
                """.formatted(csvSource(csv));
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<HourBucket> values = new ArrayList<>();
            while (rs.next()) values.add(new HourBucket(
                    rs.getTimestamp("bucket").toLocalDateTime(), rs.getLong("points"),
                    rs.getLong("trucks"), rs.getLong("moving_points")));
            return List.copyOf(values);
        }
    }

    public static long countNear(Connection connection, Path csv, int fiscalPoint, BigDecimal maxKm)
            throws SQLException, IOException {
        Objects.requireNonNull(maxKm);
        if (fiscalPoint <= 0 || maxKm.signum() < 0)
            throw new IllegalArgumentException("Invalid fiscal point or radius");
        // Application-selected file path is SQL-quoted; external values use JDBC bind parameters.
        String sql = "SELECT count(*) FROM " + csvSource(csv)
                + " WHERE nearest_fp = ? AND distance_km <= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, fiscalPoint);
            ps.setBigDecimal(2, maxKm);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public static List<String> trackingCodes(Connection connection, Path csv)
            throws SQLException, IOException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT tracking_code FROM "
                                           + csvSource(csv) + " ORDER BY 1")) {
            List<String> codes = new ArrayList<>();
            while (rs.next()) codes.add(rs.getString(1));
            return List.copyOf(codes);
        }
    }

    public static Path exportParquet(Connection connection, Path csv, Path target)
            throws SQLException, IOException {
        Path parent = Objects.requireNonNull(target).toAbsolutePath().normalize().getParent().toRealPath();
        Path output = parent.resolve(target.getFileName()).normalize();
        if (!output.getParent().equals(parent) || !output.toString().endsWith(".parquet")
                || Files.exists(output)) throw new IllegalArgumentException("Expected fresh .parquet path");
        try (Statement st = connection.createStatement()) {
            st.execute("COPY (SELECT * FROM " + csvSource(csv) + ") TO " + literal(output)
                       + " (FORMAT PARQUET, COMPRESSION ZSTD)");
        }
        if (Files.size(output) == 0) throw new IOException("Empty Parquet export");
        return output;
    }

    public static List<LatestPoint> lastPointPerTruck(Connection connection, Path parquet)
            throws SQLException, IOException {
        String sql = """
                SELECT plate, event_time, nearest_fp, distance_km, moving FROM (
                    SELECT plate, event_time, nearest_fp, distance_km, moving,
                           row_number() OVER (
                              PARTITION BY plate ORDER BY event_time DESC
                           ) AS rn
                    FROM %s
                ) WHERE rn = 1 ORDER BY plate
                """.formatted(parquetSource(parquet));
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<LatestPoint> values = new ArrayList<>();
            while (rs.next()) values.add(new LatestPoint(rs.getString("plate"),
                    rs.getTimestamp("event_time").toLocalDateTime(),
                    rs.getInt("nearest_fp"), rs.getBigDecimal("distance_km"),
                    rs.getBoolean("moving")));
            return List.copyOf(values);
        }
    }

    /** Plan output is inspected, not used as an unproven performance benchmark. */
    public static String explainParquetFilter(Connection connection, Path parquet)
            throws SQLException, IOException {
        String sql = "EXPLAIN SELECT plate FROM " + parquetSource(parquet)
                   + " WHERE nearest_fp = 7 AND distance_km < 5";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            StringBuilder plan = new StringBuilder();
            while (rs.next()) plan.append(rs.getString(1)).append('\n')
                                 .append(rs.getString(2)).append('\n');
            return plan.toString();
        }
    }
}
