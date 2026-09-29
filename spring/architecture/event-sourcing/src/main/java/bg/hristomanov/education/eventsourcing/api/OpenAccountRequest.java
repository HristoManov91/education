package bg.hristomanov.education.eventsourcing.api;

public record OpenAccountRequest(
        String accountId,
        String ownerName,
        String currency
) {
}
