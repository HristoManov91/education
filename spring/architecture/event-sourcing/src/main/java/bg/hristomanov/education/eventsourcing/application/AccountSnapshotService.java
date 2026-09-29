package bg.hristomanov.education.eventsourcing.application;

import bg.hristomanov.education.eventsourcing.domain.Account;
import bg.hristomanov.education.eventsourcing.snapshot.AccountSnapshotStore;
import org.springframework.stereotype.Service;

@Service
public class AccountSnapshotService {

    private final AccountLoader accountLoader;
    private final AccountSnapshotStore snapshotStore;

    public AccountSnapshotService(
            AccountLoader accountLoader,
            AccountSnapshotStore snapshotStore
    ) {
        this.accountLoader = accountLoader;
        this.snapshotStore = snapshotStore;
    }

    public LoadedAccount createSnapshot(String accountId) {
        LoadedAccount loaded = accountLoader.load(accountId);
        Account account = loaded.account();

        snapshotStore.save(account);

        return loaded;
    }
}
