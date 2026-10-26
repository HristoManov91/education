package bg.hristomanov.education.performance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PerformanceExperimentsTest {

    @Test
    void workloadIsDeterministicForGivenInput() {
        assertThat(StartupWorkload.checksum(3)).isEqualTo(111L);
        assertThat(StartupWorkload.checksum(3)).isEqualTo(StartupWorkload.checksum(3));
    }

    @Test
    void benchmarkInputMustBePositive() {
        assertThatThrownBy(() -> StartupWorkload.checksum(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statisticsCalculateMedianWithoutBeingFooledByOutliers() {
        assertThat(BenchmarkStatistics.medianMillis(List.of(
                100_000_000L, 110_000_000L, 120_000_000L, 999_000_000L, 105_000_000L)))
                .isEqualTo(110.0);
        assertThat(BenchmarkStatistics.medianMillis(List.of(
                100_000_000L, 200_000_000L))).isEqualTo(150.0);
    }

    @Test
    void missingSamplesCannotBeReportedAsPerformanceEvidence() {
        assertThatThrownBy(() -> BenchmarkStatistics.medianMillis(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualAotExperimentRequiresAnExistingCacheFile() {
        assertThatThrownBy(() -> AotProcessComparison.main(
                new String[]{"target/nonexistent-learning-cache-12345.aot"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AOT cache");
    }
}
