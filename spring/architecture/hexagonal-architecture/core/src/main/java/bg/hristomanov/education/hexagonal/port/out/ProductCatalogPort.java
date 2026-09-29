package bg.hristomanov.education.hexagonal.port.out;

import bg.hristomanov.education.hexagonal.domain.Product;

import java.util.Optional;

public interface ProductCatalogPort {

    Optional<Product> findBySku(String sku);

    default Product getRequired(String sku) {
        return findBySku(sku)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown product: " + sku)
                );
    }
}
