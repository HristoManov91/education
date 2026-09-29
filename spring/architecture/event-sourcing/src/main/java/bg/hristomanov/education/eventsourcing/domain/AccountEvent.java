package bg.hristomanov.education.eventsourcing.domain;

/**
 * Persisted business facts in one Account stream.
 *
 * <p>In Event Sourcing these events are not merely notifications. The ordered
 * stream is the source of truth from which Account state is reconstructed.</p>
 */
public sealed interface AccountEvent
        permits AccountOpened, MoneyDeposited, MoneyWithdrawn {
}
