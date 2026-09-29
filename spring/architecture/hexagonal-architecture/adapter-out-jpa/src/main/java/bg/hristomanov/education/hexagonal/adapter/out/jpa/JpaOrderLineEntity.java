package bg.hristomanov.education.hexagonal.adapter.out.jpa;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hex_order_lines")
public class JpaOrderLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    protected JpaOrderLineEntity() {
    }

    public JpaOrderLineEntity(
            String sku,
            String productName,
            int quantity,
            BigDecimal unitPrice
    ) {
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
