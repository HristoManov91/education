package bg.hristomanov.education.idempotency.api;

public record ApiError(
        String code,
        String message
) {
}
