package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * JSON and Instant are deliberate persistence contracts.
 * PostgreSQL JSONB and H2 JSON are NOT equivalent for plans/indexes/operators.
 */
@Entity
@Table(name = "vm22_document_metadata")
public class DocumentMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 80)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private DocumentState state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false)
    private Map<String, Object> attributes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentMetadata() {}

    public DocumentMetadata(String reference, DocumentState state,
                            Map<String, Object> attributes, Instant createdAt) {
        this.reference = Objects.requireNonNull(reference);
        this.state = Objects.requireNonNull(state);
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes));
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public DocumentState getState() { return state; }
    public Map<String, Object> getAttributes() { return attributes; }
    public Instant getCreatedAt() { return createdAt; }

    public void replaceAttributes(Map<String, Object> value) {
        this.attributes = Map.copyOf(Objects.requireNonNull(value));
    }

    public void changeState(DocumentState value) {
        this.state = Objects.requireNonNull(value);
    }
}
