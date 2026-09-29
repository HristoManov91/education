package bg.hristomanov.education.outbox.inbox;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "processed_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_processed_message_consumer_event",
                        columnNames = {"consumer_name", "event_id"}
                )
        }
)
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(
            String consumerName,
            String eventId,
            Instant processedAt
    ) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getEventId() {
        return eventId;
    }
}
