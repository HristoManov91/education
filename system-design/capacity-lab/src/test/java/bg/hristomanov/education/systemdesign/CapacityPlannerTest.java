package bg.hristomanov.education.systemdesign;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class CapacityPlannerTest {

    @Test void deterministicPeakEstimateUsesMeasuredPerReplicaCapacityAndHeadroom() {
        // 8.64 M/day => 100 average rps, 5x peak => 500 peak rps.
        CapacityPlanner.Workload workload = new CapacityPlanner.Workload(
                8_640_000, 5.0, Duration.ofMillis(200),
                0.20, 2_048, 150.0, 0.60);
        CapacityPlanner.Estimate estimate = CapacityPlanner.estimate(workload);

        assertThat(estimate.averageRps()).isEqualTo(100.0);
        assertThat(estimate.peakRps()).isEqualTo(500.0);
        assertThat(estimate.approximateMeanInFlightRequests()).isEqualTo(100);
        assertThat(estimate.peakDbWriteRps()).isEqualTo(100.0);
        assertThat(estimate.peakPayloadMiBPerSecond()).isEqualTo(0.9765625);
        // 150 * 60% = 90 rps budgeted per replica => ceil(500/90) = 6.
        assertThat(estimate.minimumReplicaEstimate()).isEqualTo(6);
    }

    @Test void oneReplicaIsMinimumAndSubsecondLatencyRoundingIsExplicit() {
        CapacityPlanner.Estimate estimate = CapacityPlanner.estimate(
                new CapacityPlanner.Workload(86_400, 1, Duration.ofMillis(100),
                        0, 0, 100, 0.80));
        assertThat(estimate.minimumReplicaEstimate()).isEqualTo(1);
        assertThat(estimate.approximateMeanInFlightRequests()).isEqualTo(1);
        assertThat(estimate.peakDbWriteRps()).isZero();
    }

    @Test void rejectsBadAssumptionsRatherThanGivingFalsePrecision() {
        assertThatThrownBy(() -> new CapacityPlanner.Workload(0, 2, Duration.ofMillis(100),
                0.5, 512, 50, 0.70)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPlanner.Workload(100, Double.NaN, Duration.ofMillis(100),
                0.5, 512, 50, 0.70)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPlanner.Workload(100, 2, Duration.ZERO,
                0.5, 512, 50, 0.70)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPlanner.Workload(100, 2, Duration.ofMillis(100),
                1.1, 512, 50, 0.70)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPlanner.Workload(100, 2, Duration.ofMillis(100),
                0.5, -512, 50, 0.70)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPlanner.Workload(100, 2, Duration.ofMillis(100),
                0.5, 512, 50, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPlanner.Workload(100, 2, Duration.ofMillis(100),
                0.5, 512, 50, 1.01)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsMassiveEstimatesInsteadOfOverflowingReplicaCount() {
        CapacityPlanner.Workload huge = new CapacityPlanner.Workload(
                Long.MAX_VALUE, 1_000_000_000, Duration.ofHours(5), 1,
                Long.MAX_VALUE, 0.001, 0.01);
        assertThatThrownBy(() -> CapacityPlanner.estimate(huge))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
