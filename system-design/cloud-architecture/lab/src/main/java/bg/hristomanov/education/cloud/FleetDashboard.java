package bg.hristomanov.education.cloud;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static bg.hristomanov.education.cloud.AnalyticsClient.Report;

/** Single fleet, serial teaching example. Not a shared multi-tenant production cache. */
public final class FleetDashboard {
    public enum ReportStatus { FRESH, STALE, UNAVAILABLE }

    public record Dashboard(int localVehicleCount, ReportStatus reportStatus,
                            Optional<Report> report) {}

    private final int localVehicleCount;
    private final AnalyticsClient analytics;
    private final Duration maxAge;
    private Report lastReport;

    public FleetDashboard(int localVehicleCount, AnalyticsClient analytics, Duration maxAge) {
        if (localVehicleCount < 0 || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Invalid dashboard parameters");
        }
        this.localVehicleCount = localVehicleCount;
        this.analytics = Objects.requireNonNull(analytics);
        this.maxAge = maxAge;
    }

    // Independent core endpoint: does not call a reporting provider.
    public int coreVehicles() { return localVehicleCount; }

    // Bad variant: optional reporting treated as a hard dependency.
    public Dashboard mandatory(Instant now) {
        var report = analytics.read();
        if (!usable(report, now)) throw new IllegalStateException("Report outside freshness contract");
        return new Dashboard(localVehicleCount, ReportStatus.FRESH, Optional.of(report));
    }

    // Good variant for a journey that can tolerate an optional report and its bounded wait.
    public Dashboard optional(Instant now) {
        try {
            var report = analytics.read();
            if (report.asOf().isAfter(now)) throw new IllegalStateException("Future source timestamp");
            if (usable(report, now) &&
                    (lastReport == null || !report.asOf().isBefore(lastReport.asOf()))) {
                lastReport = report;
                return new Dashboard(localVehicleCount, ReportStatus.FRESH, Optional.of(report));
            }
            return fallback(now);
        } catch (AnalyticsClient.RemoteUnavailable failure) {
            return fallback(now);
        }
    }

    private Dashboard fallback(Instant now) {
        return usable(lastReport, now)
                ? new Dashboard(localVehicleCount, ReportStatus.STALE, Optional.of(lastReport))
                : new Dashboard(localVehicleCount, ReportStatus.UNAVAILABLE, Optional.empty());
    }

    private boolean usable(Report report, Instant now) {
        return report != null && !report.asOf().isAfter(now)
                && Duration.between(report.asOf(), now).compareTo(maxAge) <= 0;
    }
}
