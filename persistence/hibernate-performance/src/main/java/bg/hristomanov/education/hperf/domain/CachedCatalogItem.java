package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;
import java.util.Objects;

/** VM-19: Hibernate entity cache is versioned, selective and explicitly read-write. */
@Entity
@Table(name = "vm19_catalog_items")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "vm19-catalog")
public class CachedCatalogItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Version
    private long version;

    protected CachedCatalogItem() {}

    public CachedCatalogItem(String sku, BigDecimal price) {
        this.sku = Objects.requireNonNull(sku);
        this.price = Objects.requireNonNull(price);
    }

    public void changePrice(BigDecimal newPrice) {
        Objects.requireNonNull(newPrice);
        if (newPrice.signum() < 0) {
            throw new IllegalArgumentException("price must be nonnegative");
        }
        this.price = newPrice;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public BigDecimal getPrice() { return price; }
    public long getVersion() { return version; }
}
