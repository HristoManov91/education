package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "perf_customers")
public class PerfCustomer {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "perf_customer_seq")
    @SequenceGenerator(name = "perf_customer_seq", sequenceName = "perf_customer_seq", allocationSize = 10)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.PERSIST)
    private List<PerfOrder> orders = new ArrayList<>();

    protected PerfCustomer() {}

    public PerfCustomer(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public PerfOrder placeOrder(BigDecimal total, LocalDateTime createdAt) {
        PerfOrder order = new PerfOrder(this, total, createdAt);
        orders.add(order);
        return order;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<PerfOrder> getOrders() { return List.copyOf(orders); }
}
