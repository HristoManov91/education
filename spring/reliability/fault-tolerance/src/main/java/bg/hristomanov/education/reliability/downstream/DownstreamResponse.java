package bg.hristomanov.education.reliability.downstream;

public record DownstreamResponse(
        String value,
        int attempt
) {
}
