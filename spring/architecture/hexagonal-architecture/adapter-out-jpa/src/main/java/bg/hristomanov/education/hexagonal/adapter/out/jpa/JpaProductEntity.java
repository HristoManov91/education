package bg.hristomanov.education.hexagonal.adapter.out.jpa;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hex_products")
public class JpaProductEntity {

    @Id
    @Column(length = 64)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    protected JpaProductEntity() {
    }

    public JpaProductEntity(
            String sku,
            String name,
            BigDecimal unitPrice
    ) {
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
