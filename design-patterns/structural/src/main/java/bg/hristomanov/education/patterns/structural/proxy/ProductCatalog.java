package bg.hristomanov.education.patterns.structural.proxy;

import java.math.BigDecimal;

public interface ProductCatalog {

    BigDecimal priceFor(String sku);
}
