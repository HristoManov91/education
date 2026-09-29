package bg.hristomanov.education.eventsourcing.application;

import bg.hristomanov.education.eventsourcing.domain.Account;

public record LoadedAccount(
        Account account,
        int replayedEventCount,
        boolean snapshotUsed
) {
}
