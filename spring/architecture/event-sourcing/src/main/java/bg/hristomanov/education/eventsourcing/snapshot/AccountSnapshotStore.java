package bg.hristomanov.education.eventsourcing.snapshot;

import bg.hristomanov.education.eventsourcing.domain.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Snapshot is a performance optimization, not the source of truth.
 *
 * <p>If snapshots disappear, the account can still be rebuilt from its event
 * stream. A snapshot only lets replay start from a later stream version.</p>
 */
@Service
public class AccountSnapshotStore {

    private final AccountSnapshotRepository repository;

    public AccountSnapshotStore(
            AccountSnapshotRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public void save(Account account) {
        repository.saveAndFlush(
                new AccountSnapshotEntity(
                        account.accountId(),
                        account.version(),
                        account.ownerName(),
                        account.currency(),
                        account.balance(),
                        Instant.now()
                )
        );
    }

    @Transactional(readOnly = true)
    public Optional<AccountSnapshotEntity> find(String accountId) {
        return repository.findById(accountId);
    }

    @Transactional
    public void deleteAll() {
        repository.deleteAll();
    }
}
