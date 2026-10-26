package bg.hristomanov.education.performance.benchmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic teaching simulation, NOT an actual load generator or performance benchmark.
 *
 * An open-loop client keeps its planned arrival schedule even during a server
 * stall; a closed-loop client sends its next request only after receiving a
 * response plus think time. The simulated server processes one request at a
 * time, pauses during one outage, never drops requests, and never times out.
 *
 * The purpose is to make the sampling bias of an inappropriate workload model
 * observable and testable, not to predict production p99 or real throughput.
 */
public final class BenchmarkValidityLab {

    private BenchmarkValidityLab() {
    }

    public record Scenario(int requestCount, long intervalMillis,
                           long serviceMillis, long outageStartMillis,
                           long outageEndMillis) {
        public Scenario {
            if (requestCount < 1 || requestCount > 100_000
                    || intervalMillis <= 0 || serviceMillis <= 0
                    || outageStartMillis < 0 || outageEndMillis <= outageStartMillis) {
                throw new IllegalArgumentException("Invalid workload or outage configuration");
            }
        }
    }

    public record ResponseSample(long arrivedAtMillis, long completedAtMillis) {
        public ResponseSample {
            if (arrivedAtMillis < 0 || completedAtMillis < arrivedAtMillis) {
                throw new IllegalArgumentException("Completion must follow nonnegative arrival");
            }
        }

        public long latencyMillis() {
            return completedAtMillis - arrivedAtMillis;
        }
    }

    public record Result(List<ResponseSample> samples) {
        public Result {
            samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
            if (samples.isEmpty()) {
                throw new IllegalArgumentException("At least one sample is required");
            }
        }

        /**
         * Nearest-rank percentile of completed, observed responses.
         * This is deliberately NOT a corrected HDR histogram, and excludes
         * requests that never existed in the chosen workload model.
         */
        public long percentileMillis(int percentile) {
            if (percentile < 1 || percentile > 100) {
                throw new IllegalArgumentException("Percentile must be in [1, 100]");
            }
            List<Long> sorted = samples.stream()
                    .map(ResponseSample::latencyMillis)
                    .sorted()
                    .toList();
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            return sorted.get(index);
        }

        public long completedAtMillis() {
            return samples.getLast().completedAtMillis();
        }

        public long lastArrivalAtMillis() {
            return samples.getLast().arrivedAtMillis();
        }

        public long responsesSlowerThan(long thresholdMillis) {
            return samples.stream()
                    .filter(sample -> sample.latencyMillis() > thresholdMillis)
                    .count();
        }
    }

    /**
     * Independent scheduled arrivals; pauses do not stop new requests.
     * The simulator queues them while the one server worker is unavailable.
     */
    public static Result openLoop(Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        List<ResponseSample> result = new ArrayList<>(scenario.requestCount());
        long serverFreeAt = 0L;
        for (int i = 0; i < scenario.requestCount(); i++) {
            long arrival = Math.multiplyExact(i, scenario.intervalMillis());
            long completed = complete(scenario, arrival, serverFreeAt);
            result.add(new ResponseSample(arrival, completed));
            serverFreeAt = completed;
        }
        return new Result(result);
    }

    /**
     * One virtual user: next request is scheduled AFTER the last response,
     * creating a gap in arrivals during a stall. "intervalMillis" is think
     * time here; it is NOT an externally guaranteed arrival rate.
     */
    public static Result closedLoop(Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        List<ResponseSample> result = new ArrayList<>(scenario.requestCount());
        long arrival = 0L;
        long serverFreeAt = 0L;
        for (int i = 0; i < scenario.requestCount(); i++) {
            long completed = complete(scenario, arrival, serverFreeAt);
            result.add(new ResponseSample(arrival, completed));
            serverFreeAt = completed;
            arrival = Math.addExact(completed, scenario.intervalMillis());
        }
        return new Result(result);
    }

    /**
     * Serial server with deterministic no-progress interval [outageStart,
     * outageEnd). A request already executing at the start of the outage is
     * paused for the complete outage duration.
     */
    private static long complete(Scenario scenario, long arrival, long serverFreeAt) {
        long start = Math.max(arrival, serverFreeAt);
        if (start >= scenario.outageStartMillis()
                && start < scenario.outageEndMillis()) {
            start = scenario.outageEndMillis();
        }

        long finish = Math.addExact(start, scenario.serviceMillis());
        if (start < scenario.outageStartMillis()
                && finish > scenario.outageStartMillis()) {
            finish = Math.addExact(finish,
                    scenario.outageEndMillis() - scenario.outageStartMillis());
        }
        return finish;
    }

    /**
     * Toy resource-ceiling illustration: a fast framework can be capped by an
     * independent proxy/DB. This is arithmetic, NOT a benchmark of frameworks.
     */
    public static long observedCapacity(long applicationCapacityRps,
                                        long downstreamCapacityRps) {
        if (applicationCapacityRps <= 0 || downstreamCapacityRps <= 0) {
            throw new IllegalArgumentException("Both capacities must be positive");
        }
        return Math.min(applicationCapacityRps, downstreamCapacityRps);
    }

    /**
     * Counts all attempted requests including rejected/timeouts. A high
     * response rate can coexist with unacceptable failures.
     */
    public record RunAccounting(long attempted, long succeeded, long failed,
                                long elapsedMillis) {
        public RunAccounting {
            if (attempted < 0 || succeeded < 0 || failed < 0 || elapsedMillis <= 0
                    || attempted != Math.addExact(succeeded, failed)) {
                throw new IllegalArgumentException("Inconsistent run accounting");
            }
        }

        public double successfulRequestsPerSecond() {
            return succeeded * 1_000.0 / elapsedMillis;
        }

        public double observedOutcomesPerSecond() {
            return attempted * 1_000.0 / elapsedMillis;
        }

        public double failureRatio() {
            return attempted == 0 ? 0.0 : (double) failed / attempted;
        }
    }
}
