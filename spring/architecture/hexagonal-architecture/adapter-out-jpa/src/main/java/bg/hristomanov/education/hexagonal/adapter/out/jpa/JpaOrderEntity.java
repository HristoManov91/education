package bg.hristomanov.education.hexagonal.adapter.out.jpa;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hex_orders")
public class JpaOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JoinColumn(name = "order_id", nullable = false)
    @OrderColumn(name = "line_order")
    private List<JpaOrderLineEntity> lines = new ArrayList<>();

    protected JpaOrderEntity() {
    }

    public JpaOrderEntity(
            Long id,
            String reference,
            String customerId,
            String status,
            BigDecimal totalAmount,
            List<JpaOrderLineEntity> lines
    ) {
        this.id = id;
        this.reference = reference;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.lines.addAll(lines);
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<JpaOrderLineEntity> getLines() {
        return lines;
    }
}
