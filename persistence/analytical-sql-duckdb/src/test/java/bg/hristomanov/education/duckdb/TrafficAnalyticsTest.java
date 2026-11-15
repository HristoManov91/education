package bg.hristomanov.education.duckdb;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficAnalyticsTest {

    @TempDir Path temp;

    private Path sample() throws Exception {
        return Path.of(getClass().getResource("/traffic-events.csv").toURI());
    }

    @Test
    void embeddedConnectionExecutesOrdinaryJdbcWithoutDatabaseServer() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory();
             Statement statement = c.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 42 AS answer")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("answer")).isEqualTo(42);
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void csvIsQueriedDirectlyWithDeterministicHourlyAggregation() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            assertThat(TrafficAnalytics.hourlyCsv(c, sample())).containsExactly(
                    new TrafficAnalytics.HourBucket(
                            LocalDateTime.of(2026, 11, 1, 10, 0), 4, 3, 3),
                    new TrafficAnalytics.HourBucket(
                            LocalDateTime.of(2026, 11, 1, 11, 0), 4, 3, 3),
                    new TrafficAnalytics.HourBucket(
                            LocalDateTime.of(2026, 11, 1, 12, 0), 2, 2, 1)
            );
        }
    }

    @Test
    void fiscalPointAndDecimalRadiusAreBoundAsJDBCParameters() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            assertThat(TrafficAnalytics.countNear(c, sample(), 7, new BigDecimal("5.000")))
                    .isEqualTo(5);
            assertThat(TrafficAnalytics.countNear(c, sample(), 7, new BigDecimal("4.999")))
                    .isEqualTo(4);
            assertThat(TrafficAnalytics.countNear(c, sample(), 8, BigDecimal.ONE))
                    .isEqualTo(1);
        }
    }

    @Test
    void invalidCallerRadiusOrFiscalPointIsRejected() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            Path input = sample();
            assertThatThrownBy(() -> TrafficAnalytics.countNear(c, input, -1, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrafficAnalytics.countNear(c, input, 7, new BigDecimal("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void leadingZerosInBusinessIdentifiersAreNotSilentlyLostByCsvSniffing() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            assertThat(TrafficAnalytics.trackingCodes(c, sample()))
                    .containsExactly("00001", "00002", "00003", "00004");
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT CAST('00001' AS INTEGER)")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void parquetExportUsesRealColumnarFileAndPreservesAllSourceRows() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            Path parquet = TrafficAnalytics.exportParquet(c, sample(), temp.resolve("events.parquet"));
            assertThat(parquet).exists().isNotEmptyFile();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT count(*) AS total, count(DISTINCT plate) AS trucks "
                         + "FROM read_parquet('" + parquet.toString().replace("'", "''") + "')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("total")).isEqualTo(10);
                assertThat(rs.getLong("trucks")).isEqualTo(4);
            }
        }
    }

    @Test
    void windowFunctionFindsLastRecordedPointPerTruckFromParquet() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            Path parquet = TrafficAnalytics.exportParquet(c, sample(), temp.resolve("events.parquet"));
            List<TrafficAnalytics.LatestPoint> rows =
                    TrafficAnalytics.lastPointPerTruck(c, parquet);
            assertThat(rows).hasSize(4);
            assertThat(rows.get(0).plate()).isEqualTo("BG001");
            assertThat(rows.get(0).at()).isEqualTo(LocalDateTime.of(2026, 11, 1, 11, 15));
            assertThat(rows.get(0).distanceKm()).isEqualByComparingTo("1.000");
            assertThat(rows.get(1).plate()).isEqualTo("BG002");
            assertThat(rows.get(1).at()).isEqualTo(LocalDateTime.of(2026, 11, 1, 12, 5));
            assertThat(rows.get(1).fiscalPoint()).isEqualTo(8);
            assertThat(rows.get(2).plate()).isEqualTo("BG003");
            assertThat(rows.get(2).distanceKm()).isEqualByComparingTo("8.000");
            assertThat(rows.get(2).moving()).isFalse();
            assertThat(rows.get(3).plate()).isEqualTo("BG004");
        }
    }

    @Test
    void explainReturnsRealPlanWithoutAssumingAStableOperatorNameOrSpeedup() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            Path parquet = TrafficAnalytics.exportParquet(c, sample(), temp.resolve("events.parquet"));
            assertThat(TrafficAnalytics.explainParquetFilter(c, parquet))
                    .isNotBlank()
                    .containsIgnoringCase("plate");
            // Operator spelling and whether pruning is profitable depend on
            // DuckDB release, file layout and measured workload.
        }
    }

    @Test
    void noPhysicalDuckdbTableIsNecessaryForDirectCsvAnalyticalQueries() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            assertThat(TrafficAnalytics.hourlyCsv(c, sample())).hasSize(3);
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM information_schema.tables "
                         + "WHERE table_schema = 'main' AND table_name = 'traffic_events'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    void persistentDatabaseFileSurvivesConnectionReopenUnlikeInMemoryConnection() throws Exception {
        String url = "jdbc:duckdb:" + temp.resolve("analytics.duckdb");
        try (Connection first = DriverManager.getConnection(url);
             Statement st = first.createStatement()) {
            st.execute("CREATE TABLE dimensions AS SELECT 7 AS fiscal_point, 'north' AS label");
        }
        try (Connection reopened = DriverManager.getConnection(url);
             Statement st = reopened.createStatement();
             ResultSet rs = st.executeQuery("SELECT label FROM dimensions WHERE fiscal_point = 7")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("north");
        }
    }

    @Test
    void parquetExporterDoesNotSilentlyOverwriteExistingEvidence() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            Path output = temp.resolve("events.parquet");
            TrafficAnalytics.exportParquet(c, sample(), output);
            assertThatThrownBy(() -> TrafficAnalytics.exportParquet(c, sample(), output))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fresh");
        }
    }

    @Test
    void nonexistentFileAndWrongOutputExtensionFailBeforeAnySqlExecution() throws Exception {
        try (Connection c = TrafficAnalytics.inMemory()) {
            assertThatThrownBy(() -> TrafficAnalytics.hourlyCsv(c, temp.resolve("missing.csv")))
                    .isInstanceOf(java.nio.file.NoSuchFileException.class);
            assertThatThrownBy(() -> TrafficAnalytics.exportParquet(
                    c, sample(), temp.resolve("wrong.txt")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
