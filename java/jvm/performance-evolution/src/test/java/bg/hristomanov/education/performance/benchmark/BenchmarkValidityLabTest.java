package bg.hristomanov.education.performance.benchmark;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

class BenchmarkValidityLabTest {

    private static final BenchmarkValidityLab.Scenario STALL =
            new BenchmarkValidityLab.Scenario(
                    200, 100, 10, 1_000, 3_000);

    @Test
    void openLoopKeepsSendingWhileClosedLoopStopsDuringOutage() {
        var open = BenchmarkValidityLab.openLoop(STALL);
        var closed = BenchmarkValidityLab.closedLoop(STALL);

        assertThat(open.samples()).hasSize(200);
        assertThat(closed.samples()).hasSize(200);

        // 200 planned arrivals every 100ms, independently of service delays.
        assertThat(open.lastArrivalAtMillis()).isEqualTo(19_900);
        // One closed-loop virtual user cannot submit the next request while waiting.
        assertThat(closed.lastArrivalAtMillis()).isGreaterThan(open.lastArrivalAtMillis());

        assertThat(open.samples().get(10).arrivedAtMillis()).isEqualTo(1_000);
        assertThat(open.samples().get(11).arrivedAtMillis()).isEqualTo(1_100);
        assertThat(closed.samples().get(10).latencyMillis()).isEqualTo(1_910);
        assertThat(closed.samples().get(11).latencyMillis()).isEqualTo(10);
    }

    @Test
    void closedLoopP99HidesMostOfTheFixedArrivalRateOutage() {
        var open = BenchmarkValidityLab.openLoop(STALL);
        var closed = BenchmarkValidityLab.closedLoop(STALL);

        // Deterministic theoretical timings for this toy single-worker server.
        // These are NOT empirical HTTP or production benchmark timings.
        assertThat(open.percentileMillis(95)).isEqualTo(1_110);
        assertThat(open.percentileMillis(99)).isEqualTo(1_830);

        assertThat(closed.percentileMillis(95)).isEqualTo(10);
        assertThat(closed.percentileMillis(99)).isEqualTo(10);
        // The worst closed-loop request DID see the outage, but 1 / 200
        // slow samples does not change its nearest-rank p99.
        assertThat(closed.percentileMillis(100)).isEqualTo(1_910);
        assertThat(open.responsesSlowerThan(1_000))
                .isGreaterThan(closed.responsesSlowerThan(1_000));
    }

    @Test
    void immutableResultCannotBeSilentlyModifiedAfterMeasurements() {
        var original = new ArrayList<BenchmarkValidityLab.ResponseSample>();
        original.add(new BenchmarkValidityLab.ResponseSample(0, 10));
        var result = new BenchmarkValidityLab.Result(original);

        original.clear();
        assertThat(result.samples()).hasSize(1);
        assertThatThrownBy(() -> result.samples().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidPercentilesOrTimeRangesFailInsteadOfPublishingStatistics() {
        assertThatThrownBy(() -> new BenchmarkValidityLab.Scenario(
                1, 100, 10, 2_000, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BenchmarkValidityLab.ResponseSample(200, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BenchmarkValidityLab.openLoop(STALL).percentileMillis(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BenchmarkValidityLab.openLoop(STALL).percentileMillis(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sharedProxyCanMakeDifferentApplicationCapacitiesLookIdentical() {
        // Toy capacities, NOT Quarkus/Spring benchmark numbers.
        assertThat(BenchmarkValidityLab.observedCapacity(24_000, 12_000))
                .isEqualTo(12_000);
        assertThat(BenchmarkValidityLab.observedCapacity(13_000, 12_000))
                .isEqualTo(12_000);

        // Higher shared ceiling exposes a difference in application capacity.
        assertThat(BenchmarkValidityLab.observedCapacity(24_000, 18_000))
                .isEqualTo(18_000);
        assertThat(BenchmarkValidityLab.observedCapacity(13_000, 18_000))
                .isEqualTo(13_000);
    }

    @Test
    void highOutcomeRateCanHideFailedRequests() {
        var run = new BenchmarkValidityLab.RunAccounting(
                1_000, 900, 100, 1_000);

        assertThat(run.observedOutcomesPerSecond()).isEqualTo(1_000.0);
        assertThat(run.successfulRequestsPerSecond()).isEqualTo(900.0);
        assertThat(run.failureRatio()).isEqualTo(0.10);
    }

    @Test
    void accountingMustIncludeAllCompletedOutcomesExactlyOnce() {
        assertThatThrownBy(() -> new BenchmarkValidityLab.RunAccounting(
                1_000, 900, 50, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BenchmarkValidityLab.RunAccounting(
                100, 100, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
