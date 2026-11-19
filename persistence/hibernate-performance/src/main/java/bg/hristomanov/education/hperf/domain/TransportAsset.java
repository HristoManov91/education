package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.util.Objects;

/** VM-28: JOINED storage preserves subtype-specific NOT NULL columns. */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "asset_kind")
@Table(name = "vm28_assets")
public abstract class TransportAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    protected TransportAsset() {}

    protected TransportAsset(String code) {
        this.code = Objects.requireNonNull(code);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
}
