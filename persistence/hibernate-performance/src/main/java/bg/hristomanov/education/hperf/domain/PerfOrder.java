package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "perf_orders")
public class PerfOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "perf_order_seq")
    @SequenceGenerator(name = "perf_order_seq", sequenceName = "perf_order_seq", allocationSize = 10)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private PerfCustomer customer;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected PerfOrder() {}

    PerfOrder(PerfCustomer customer, BigDecimal total, LocalDateTime createdAt) {
        this.customer = Objects.requireNonNull(customer);
        this.total = Objects.requireNonNull(total);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getId() { return id; }
    public PerfCustomer getCustomer() { return customer; }
    public BigDecimal getTotal() { return total; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
