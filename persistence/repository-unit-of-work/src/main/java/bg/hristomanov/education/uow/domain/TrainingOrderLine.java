package bg.hristomanov.education.uow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "training_order_lines")
public class TrainingOrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TrainingOrder order;

    protected TrainingOrderLine() { }

    TrainingOrderLine(String description) {
        this.description = Objects.requireNonNull(description);
    }

    void attachTo(TrainingOrder parent) {
        this.order = Objects.requireNonNull(parent);
    }

    void detachFromParent() {
        this.order = null; // The orphanRemoval on the parent schedules DELETE.
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }
}
