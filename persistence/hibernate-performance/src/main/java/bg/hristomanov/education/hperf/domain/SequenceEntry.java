package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "perf_sequence_entries")
public class SequenceEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "perf_entry_sequence")
    @SequenceGenerator(name = "perf_entry_sequence", sequenceName = "perf_entry_sequence", allocationSize = 20)
    private Long id;

    @Column(nullable = false)
    private String description;

    protected SequenceEntry() {}

    public SequenceEntry(String description) {
        this.description = description;
    }

    public Long getId() {
        return id;
    }
}
