package bg.hristomanov.education.patterns.structural.proxy;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoteProductCatalog implements ProductCatalog {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public BigDecimal priceFor(String sku) {
        calls.incrementAndGet();
        return new BigDecimal("199.90");
    }

    public int callCount() {
        return calls.get();
    }
}
