package bg.hristomanov.education.eventsourcing.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredEventRepository
        extends JpaRepository<StoredEventEntity, Long> {

    List<StoredEventEntity>
    findByStreamIdOrderByStreamVersionAsc(String streamId);

    List<StoredEventEntity>
    findByStreamIdAndStreamVersionLessThanEqualOrderByStreamVersionAsc(
            String streamId,
            long streamVersion
    );

    List<StoredEventEntity>
    findByStreamIdAndStreamVersionGreaterThanOrderByStreamVersionAsc(
            String streamId,
            long streamVersion
    );

    List<StoredEventEntity>
    findByGlobalPositionGreaterThanOrderByGlobalPositionAsc(
            long globalPosition
    );

    Optional<StoredEventEntity>
    findFirstByStreamIdOrderByStreamVersionDesc(String streamId);
}
