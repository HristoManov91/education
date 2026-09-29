package bg.hristomanov.education.eventsourcing.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSnapshotRepository
        extends JpaRepository<AccountSnapshotEntity, String> {
}
