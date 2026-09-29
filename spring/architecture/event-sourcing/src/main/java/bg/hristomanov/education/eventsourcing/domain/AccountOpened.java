package bg.hristomanov.education.eventsourcing.domain;

public record AccountOpened(
        String accountId,
        String ownerName,
        String currency
) implements AccountEvent {
}
