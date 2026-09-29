package bg.hristomanov.education.eventsourcing.store;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "es_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_es_event_id",
                        columnNames = "event_id"
                ),
                @UniqueConstraint(
                        name = "uk_es_stream_version",
                        columnNames = {
                                "stream_id",
                                "stream_version"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_es_stream_version",
                        columnList = "stream_id,stream_version"
                )
        }
)
public class StoredEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "global_position")
    private Long globalPosition;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "stream_id", nullable = false, length = 100)
    private String streamId;

    @Column(name = "stream_version", nullable = false)
    private long streamVersion;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_schema_version", nullable = false)
    private int eventSchemaVersion;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected StoredEventEntity() {
    }

    public StoredEventEntity(
            String eventId,
            String streamId,
            long streamVersion,
            String eventType,
            int eventSchemaVersion,
            String payload,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.streamId = streamId;
        this.streamVersion = streamVersion;
        this.eventType = eventType;
        this.eventSchemaVersion = eventSchemaVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public Long getGlobalPosition() {
        return globalPosition;
    }

    public String getEventId() {
        return eventId;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getStreamVersion() {
        return streamVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventSchemaVersion() {
        return eventSchemaVersion;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
