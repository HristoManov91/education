package bg.hristomanov.education.cqrs.api;

public record CommandResult(
        long orderId,
        String projectionMode
) {
}
