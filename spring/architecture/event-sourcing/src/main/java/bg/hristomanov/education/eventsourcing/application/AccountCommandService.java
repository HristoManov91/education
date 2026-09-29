package bg.hristomanov.education.eventsourcing.application;

import bg.hristomanov.education.eventsourcing.domain.Account;
import bg.hristomanov.education.eventsourcing.store.EventStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Command side:
 *
 * <pre>
 * load history -> rehydrate aggregate -> decide -> append new events
 * </pre>
 */
@Service
public class AccountCommandService {

    private final EventStore eventStore;
    private final AccountLoader accountLoader;

    public AccountCommandService(
            EventStore eventStore,
            AccountLoader accountLoader
    ) {
        this.eventStore = eventStore;
        this.accountLoader = accountLoader;
    }

    public Account open(
            String accountId,
            String ownerName,
            String currency
    ) {
        Account account = Account.open(
                accountId,
                ownerName,
                currency
        );

        eventStore.append(
                accountId,
                Account.NO_STREAM_VERSION,
                account.uncommittedEvents()
        );

        account.markCommitted();
        return account;
    }

    public Account deposit(
            String accountId,
            BigDecimal amount,
            String reference
    ) {
        Account account =
                accountLoader.loadUsingSnapshot(accountId).account();

        long expectedVersion = account.version();

        account.deposit(amount, reference);

        eventStore.append(
                accountId,
                expectedVersion,
                account.uncommittedEvents()
        );

        account.markCommitted();
        return account;
    }

    public Account withdraw(
            String accountId,
            BigDecimal amount,
            String reference
    ) {
        Account account =
                accountLoader.loadUsingSnapshot(accountId).account();

        long expectedVersion = account.version();

        account.withdraw(amount, reference);

        eventStore.append(
                accountId,
                expectedVersion,
                account.uncommittedEvents()
        );

        account.markCommitted();
        return account;
    }
}
