package bg.hristomanov.education.eventsourcing.domain;

import bg.hristomanov.education.eventsourcing.store.StoredEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event-sourced aggregate.
 *
 * <p>There is no JPA-mapped Account current-state row. Durable state lives in
 * the event stream. This object is a working copy rebuilt by replay.</p>
 */
public final class Account {

    public static final long NO_STREAM_VERSION = -1L;

    private String accountId;
    private String ownerName;
    private String currency;
    private BigDecimal balance = BigDecimal.ZERO;
    private long version = NO_STREAM_VERSION;

    private final List<AccountEvent> uncommittedEvents =
            new ArrayList<>();

    private Account() {
    }

    public static Account open(
            String accountId,
            String ownerName,
            String currency
    ) {
        Account account = new Account();

        account.raise(
                new AccountOpened(
                        requireText(accountId, "accountId"),
                        requireText(ownerName, "ownerName"),
                        requireText(currency, "currency").toUpperCase()
                )
        );

        return account;
    }

    public static Account rehydrate(List<StoredEvent> history) {
        if (history.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot rehydrate account from empty stream"
            );
        }

        Account account = new Account();

        for (StoredEvent storedEvent : history) {
            account.apply(
                    storedEvent.event(),
                    storedEvent.streamVersion()
            );
        }

        return account;
    }

    public static Account fromSnapshot(
            String accountId,
            String ownerName,
            String currency,
            BigDecimal balance,
            long version
    ) {
        Account account = new Account();
        account.accountId = requireText(accountId, "accountId");
        account.ownerName = requireText(ownerName, "ownerName");
        account.currency = requireText(currency, "currency");
        account.balance = Objects.requireNonNull(balance);
        account.version = version;
        return account;
    }

    public void replay(List<StoredEvent> events) {
        for (StoredEvent storedEvent : events) {
            if (storedEvent.streamVersion() <= version) {
                continue;
            }
            apply(storedEvent.event(), storedEvent.streamVersion());
        }
    }

    public void deposit(
            BigDecimal amount,
            String reference
    ) {
        validatePositive(amount);
        raise(
                new MoneyDeposited(
                        amount,
                        requireText(reference, "reference")
                )
        );
    }

    public void withdraw(
            BigDecimal amount,
            String reference
    ) {
        validatePositive(amount);

        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient funds: balance="
                            + balance
                            + ", requested="
                            + amount
            );
        }

        raise(
                new MoneyWithdrawn(
                        amount,
                        requireText(reference, "reference")
                )
        );
    }

    public List<AccountEvent> uncommittedEvents() {
        return List.copyOf(uncommittedEvents);
    }

    public void markCommitted() {
        uncommittedEvents.clear();
    }

    public String accountId() {
        return accountId;
    }

    public String ownerName() {
        return ownerName;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal balance() {
        return balance;
    }

    public long version() {
        return version;
    }

    private void raise(AccountEvent event) {
        long nextVersion = version + 1;
        apply(event, nextVersion);
        uncommittedEvents.add(event);
    }

    private void apply(
            AccountEvent event,
            long streamVersion
    ) {
        switch (event) {
            case AccountOpened opened -> {
                this.accountId = opened.accountId();
                this.ownerName = opened.ownerName();
                this.currency = opened.currency();
                this.balance = BigDecimal.ZERO;
            }
            case MoneyDeposited deposited ->
                    this.balance = balance.add(deposited.amount());
            case MoneyWithdrawn withdrawn ->
                    this.balance = balance.subtract(withdrawn.amount());
        }

        this.version = streamVersion;
    }

    private static void validatePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be positive"
            );
        }
    }

    private static String requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
        return value.trim();
    }
}
