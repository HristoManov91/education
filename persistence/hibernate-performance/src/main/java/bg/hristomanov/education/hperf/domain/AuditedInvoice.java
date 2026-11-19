package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.Objects;

/** VM-27: entity-level history; CDC/outbox is a different integration guarantee. */
@Audited
@Entity
@Table(name = "vm27_invoices")
public class AuditedInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String reference;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    protected AuditedInvoice() {}

    public AuditedInvoice(String reference, BigDecimal total) {
        this.reference = Objects.requireNonNull(reference);
        this.total = Objects.requireNonNull(total);
        this.status = "DRAFT";
    }

    public void approve() {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only a draft invoice can be approved");
        }
        status = "APPROVED";
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
}
