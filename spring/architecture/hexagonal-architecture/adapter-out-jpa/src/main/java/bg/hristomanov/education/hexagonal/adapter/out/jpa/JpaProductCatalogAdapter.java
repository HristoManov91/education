package bg.hristomanov.education.hexagonal.adapter.out.jpa;

import bg.hristomanov.education.hexagonal.domain.Product;
import bg.hristomanov.education.hexagonal.port.out.ProductCatalogPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaProductCatalogAdapter
        implements ProductCatalogPort {

    private final SpringDataProductRepository repository;

    public JpaProductCatalogAdapter(
            SpringDataProductRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return repository.findById(sku)
                .map(entity ->
                        new Product(
                                entity.getSku(),
                                entity.getName(),
                                entity.getUnitPrice()
                        )
                );
    }
}
