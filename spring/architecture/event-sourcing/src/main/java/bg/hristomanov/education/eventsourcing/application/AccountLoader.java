package bg.hristomanov.education.eventsourcing.application;

import bg.hristomanov.education.eventsourcing.domain.Account;
import bg.hristomanov.education.eventsourcing.snapshot.AccountSnapshotEntity;
import bg.hristomanov.education.eventsourcing.snapshot.AccountSnapshotStore;
import bg.hristomanov.education.eventsourcing.store.EventStore;
import bg.hristomanov.education.eventsourcing.store.EventStream;
import bg.hristomanov.education.eventsourcing.store.StoredEvent;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rebuilds the working aggregate from its event history.
 */
@Service
public class AccountLoader {

    private final EventStore eventStore;
    private final AccountSnapshotStore snapshotStore;

    public AccountLoader(
            EventStore eventStore,
            AccountSnapshotStore snapshotStore
    ) {
        this.eventStore = eventStore;
        this.snapshotStore = snapshotStore;
    }

    public LoadedAccount load(String accountId) {
        EventStream stream = eventStore.readStream(accountId);

        if (stream.events().isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown account stream: " + accountId
            );
        }

        return new LoadedAccount(
                Account.rehydrate(stream.events()),
                stream.events().size(),
                false
        );
    }

    public LoadedAccount loadAtVersion(
            String accountId,
            long inclusiveVersion
    ) {
        EventStream stream = eventStore.readStreamUpToVersion(
                accountId,
                inclusiveVersion
        );

        if (stream.events().isEmpty()) {
            throw new IllegalArgumentException(
                    "No account history at version "
                            + inclusiveVersion
                            + " for "
                            + accountId
            );
        }

        return new LoadedAccount(
                Account.rehydrate(stream.events()),
                stream.events().size(),
                false
        );
    }

    public LoadedAccount loadUsingSnapshot(String accountId) {
        return snapshotStore.find(accountId)
                .map(snapshot -> loadFromSnapshot(accountId, snapshot))
                .orElseGet(() -> load(accountId));
    }

    private LoadedAccount loadFromSnapshot(
            String accountId,
            AccountSnapshotEntity snapshot
    ) {
        Account account = Account.fromSnapshot(
                snapshot.getAccountId(),
                snapshot.getOwnerName(),
                snapshot.getCurrency(),
                snapshot.getBalance(),
                snapshot.getStreamVersion()
        );

        List<StoredEvent> tail = eventStore.readStreamAfterVersion(
                accountId,
                snapshot.getStreamVersion()
        );

        account.replay(tail);

        return new LoadedAccount(
                account,
                tail.size(),
                true
        );
    }
}
