package bg.hristomanov.education.eventsourcing.api;

import bg.hristomanov.education.eventsourcing.application.LoadedAccount;
import bg.hristomanov.education.eventsourcing.domain.Account;

import java.math.BigDecimal;

public record AccountStateResponse(
        String accountId,
        String ownerName,
        String currency,
        BigDecimal balance,
        long streamVersion,
        int replayedEvents,
        boolean snapshotUsed
) {

    public static AccountStateResponse from(
            LoadedAccount loaded
    ) {
        Account account = loaded.account();

        return new AccountStateResponse(
                account.accountId(),
                account.ownerName(),
                account.currency(),
                account.balance(),
                account.version(),
                loaded.replayedEventCount(),
                loaded.snapshotUsed()
        );
    }

    public static AccountStateResponse from(
            Account account
    ) {
        return new AccountStateResponse(
                account.accountId(),
                account.ownerName(),
                account.currency(),
                account.balance(),
                account.version(),
                0,
                false
        );
    }
}
