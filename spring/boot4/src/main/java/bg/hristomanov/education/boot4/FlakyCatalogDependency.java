package bg.hristomanov.education.boot4;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Детерминиран fake downstream, без HTTP sleep и без външна инфраструктура.
 * Първите две заявки се провалят; третата успява. Тестът проверява реалния брой опити.
 */
@Component
public class FlakyCatalogDependency {

    private final AtomicInteger attempts = new AtomicInteger();

    public String read() {
        int current = attempts.incrementAndGet();
        if (current <= 2) {
            throw new TransientCatalogException("temporary catalog failure");
        }
        return "catalog-ready";
    }

    public int attempts() {
        return attempts.get();
    }

    public void reset() {
        attempts.set(0);
    }
}
