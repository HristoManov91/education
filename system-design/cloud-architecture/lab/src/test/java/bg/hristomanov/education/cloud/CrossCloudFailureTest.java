package bg.hristomanov.education.cloud;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static bg.hristomanov.education.cloud.FleetDashboard.ReportStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class CrossCloudFailureTest {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>("7," + NOW);
    private HttpServer server;
    private URI uri;
    private FleetDashboard dashboard;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/report", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/report");
        dashboard = dashboard(Duration.ofSeconds(3));
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    private FleetDashboard dashboard(Duration timeout) {
        return new FleetDashboard(12, new AnalyticsClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), uri, timeout),
                Duration.ofMinutes(15));
    }

    @Test
    void mandatoryAnalyticsFailsButCoreIsIndependent() {
        status.set(503);
        assertThrows(AnalyticsClient.RemoteUnavailable.class, () -> dashboard.mandatory(NOW));
        int before = requests.get();
        assertEquals(12, dashboard.coreVehicles());
        assertEquals(before, requests.get());
        var optional = dashboard.optional(NOW);
        assertEquals(12, optional.localVehicleCount());
        assertEquals(UNAVAILABLE, optional.reportStatus());
        assertTrue(optional.report().isEmpty());
    }

    @Test
    void warmReportFallsBackOnlyWithinItsSourceFreshnessLimit() {
        var good = dashboard.optional(NOW);
        assertEquals(FRESH, good.reportStatus());
        assertEquals(7, good.report().orElseThrow().count());
        status.set(503);
        var cached = dashboard.optional(NOW.plusSeconds(900));
        assertEquals(STALE, cached.reportStatus());
        assertEquals(NOW, cached.report().orElseThrow().asOf());
        var expired = dashboard.optional(NOW.plusSeconds(901));
        assertEquals(UNAVAILABLE, expired.reportStatus());
        assertTrue(expired.report().isEmpty());
    }

    @Test
    void recoveryRefreshesTheSnapshot() {
        dashboard.optional(NOW);
        status.set(503);
        assertEquals(STALE, dashboard.optional(NOW.plusSeconds(1)).reportStatus());
        status.set(200);
        body.set("8," + NOW.plusSeconds(2));
        var recovered = dashboard.optional(NOW.plusSeconds(2));
        assertEquals(FRESH, recovered.reportStatus());
        assertEquals(8, recovered.report().orElseThrow().count());
    }

    @Test
    void authAndMalformedResponsesAreNotMaskedByFallback() {
        dashboard.optional(NOW);
        status.set(403);
        assertThrows(IllegalStateException.class, () -> dashboard.optional(NOW));
        status.set(200);
        body.set("not-a-report");
        assertThrows(IllegalArgumentException.class, () -> dashboard.optional(NOW));
    }

    @Test
    void oldSnapshotCannotOverwriteNewerCachedReport() {
        dashboard.optional(NOW);
        body.set("2," + NOW.minusSeconds(1));
        var result = dashboard.optional(NOW);
        assertEquals(STALE, result.reportStatus());
        assertEquals(7, result.report().orElseThrow().count());
        body.set("2," + NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> dashboard.optional(NOW));
    }

    @Test
    void staleSourceIsNotFreshEvenWithSuccessfulHttp() {
        body.set("7," + NOW.minusSeconds(901));
        assertEquals(UNAVAILABLE, dashboard.optional(NOW).reportStatus());
    }

    @Test
    void deadlineHandlesAResponseThatNeverStartsWithinBudget() {
        var release = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            try (exchange) {
                release.await(5, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        uri = uri.resolve("/slow");
        try {
            var slowDashboard = dashboard(Duration.ofMillis(150));
            assertEquals(UNAVAILABLE, slowDashboard.optional(NOW).reportStatus());
            assertEquals(12, slowDashboard.coreVehicles());
        } finally {
            release.countDown();
        }
    }
}
