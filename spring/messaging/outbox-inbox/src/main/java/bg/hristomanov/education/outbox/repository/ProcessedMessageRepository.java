package bg.hristomanov.education.outbox.repository;

import bg.hristomanov.education.outbox.inbox.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {

    boolean existsByConsumerNameAndEventId(
            String consumerName,
            String eventId
    );
}
