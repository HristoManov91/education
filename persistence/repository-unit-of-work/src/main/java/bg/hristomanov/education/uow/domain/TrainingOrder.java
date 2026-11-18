package bg.hristomanov.education.uow.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Educational aggregate with explicitly synchronized bidirectional relationship. */
@Entity
@Table(name = "training_orders")
public class TrainingOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String reference;

    // Child's ManyToOne owns the foreign key; cascade follows aggregate ownership.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<TrainingOrderLine> lines = new ArrayList<>();

    protected TrainingOrder() { }

    public TrainingOrder(String reference) {
        this.reference = Objects.requireNonNull(reference);
    }

    public void addLine(String description) {
        var line = new TrainingOrderLine(description);
        lines.add(line);
        line.attachTo(this);
    }

    public void removeLine(String description) {
        TrainingOrderLine match = lines.stream()
                .filter(line -> line.getDescription().equals(description))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown line: " + description));
        lines.remove(match);
        match.detachFromParent();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public List<TrainingOrderLine> getLines() {
        return List.copyOf(lines);
    }

    // No Lombok-generated equals/hashCode/toString: loading proxies and mutable
    // relationships from those methods leads to surprising SQL and recursion.
}
