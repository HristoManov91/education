package bg.hristomanov.education.systemdesign;

import java.time.Duration;
import java.util.Objects;

/**
 * Educational workload model. Estimates are deterministic arithmetic, not load tests.
 *
 * <p>Little's Law approximation L = lambda * W requires a stable system and mean values.
 * Do NOT use this estimate as a p99 guarantee, pool size, or a measured throughput.
 */
public final class CapacityPlanner {
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double BYTES_PER_MIB = 1_048_576.0;

    private CapacityPlanner() {}

    /**
     * @param dailyRequests workload requests per 24h, including accepted/failed outcomes per chosen definition
     * @param peakToAverageRatio estimated peak demand relative to 24h average, >= 1
     * @param meanEndToEndLatency mean duration for the modeled request class (not p99)
     * @param writeFraction share of requests resulting in one primary DB write, [0,1]
     * @param meanResponseBytes assumed response payload bytes (excludes protocol/network overhead)
     * @param measuredSustainableRpsPerReplica observed load-test result, NOT a capacity estimate
     * @param targetUtilization fraction of measured sustainable capacity to budget (0,1]
     */
    public record Workload(
            long dailyRequests,
            double peakToAverageRatio,
            Duration meanEndToEndLatency,
            double writeFraction,
            long meanResponseBytes,
            double measuredSustainableRpsPerReplica,
            double targetUtilization) {

        public Workload {
            if (dailyRequests <= 0) throw new IllegalArgumentException("dailyRequests must be positive");
            if (!Double.isFinite(peakToAverageRatio) || peakToAverageRatio < 1) {
                throw new IllegalArgumentException("peakToAverageRatio must be finite and >= 1");
            }
            Objects.requireNonNull(meanEndToEndLatency, "meanEndToEndLatency");
            if (meanEndToEndLatency.isNegative() || meanEndToEndLatency.isZero()) {
                throw new IllegalArgumentException("meanEndToEndLatency must be positive");
            }
            if (!Double.isFinite(writeFraction) || writeFraction < 0 || writeFraction > 1) {
                throw new IllegalArgumentException("writeFraction must be between 0 and 1");
            }
            if (meanResponseBytes < 0) throw new IllegalArgumentException("meanResponseBytes must be nonnegative");
            if (!Double.isFinite(measuredSustainableRpsPerReplica) || measuredSustainableRpsPerReplica <= 0) {
                throw new IllegalArgumentException("measuredSustainableRpsPerReplica must be positive");
            }
            if (!Double.isFinite(targetUtilization) || targetUtilization <= 0 || targetUtilization > 1) {
                throw new IllegalArgumentException("targetUtilization must be within (0, 1]");
            }
        }
    }

    public record Estimate(
            double averageRps,
            double peakRps,
            long approximateMeanInFlightRequests,
            double peakDbWriteRps,
            double peakPayloadMiBPerSecond,
            int minimumReplicaEstimate) {}

    public static Estimate estimate(Workload workload) {
        Objects.requireNonNull(workload, "workload");
        double averageRps = workload.dailyRequests() / SECONDS_PER_DAY;
        double peakRps = averageRps * workload.peakToAverageRatio();
        double seconds = workload.meanEndToEndLatency().getSeconds()
                + workload.meanEndToEndLatency().getNano() / 1_000_000_000.0;
        double inFlight = peakRps * seconds;
        double writeRps = peakRps * workload.writeFraction();
        double payloadMiBps = peakRps * workload.meanResponseBytes() / BYTES_PER_MIB;
        double replicas = peakRps
                / (workload.measuredSustainableRpsPerReplica() * workload.targetUtilization());

        // Avoid silent numeric overflow or meaningless infinity-sized workload estimates.
        if (!Double.isFinite(peakRps) || !Double.isFinite(inFlight) || !Double.isFinite(writeRps)
                || !Double.isFinite(payloadMiBps) || !Double.isFinite(replicas)
                || inFlight > Long.MAX_VALUE || replicas > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("workload exceeds estimate numeric range");
        }

        return new Estimate(averageRps, peakRps, (long) Math.ceil(inFlight),
                writeRps, payloadMiBps, Math.max(1, (int) Math.ceil(replicas)));
    }
}
