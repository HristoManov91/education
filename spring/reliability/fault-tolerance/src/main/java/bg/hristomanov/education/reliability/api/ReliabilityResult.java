package bg.hristomanov.education.reliability.api;

public record ReliabilityResult(
        String policy,
        String value,
        int downstreamAttempts,
        String circuitState
) {
}
