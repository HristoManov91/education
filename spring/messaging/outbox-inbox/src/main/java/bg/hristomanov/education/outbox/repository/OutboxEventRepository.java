package bg.hristomanov.education.outbox.repository;

import bg.hristomanov.education.outbox.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    Optional<OutboxEvent> findFirstByPublishedAtIsNullOrderByCreatedAtAscIdAsc();

    long countByPublishedAtIsNull();
}
