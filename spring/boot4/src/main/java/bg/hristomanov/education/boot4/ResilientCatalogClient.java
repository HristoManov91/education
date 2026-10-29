package bg.hristomanov.education.boot4;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Retry boundary: proxy-то обгръща извикване от ДРУГ Spring bean.
 * Само read-only операция и конкретен transient exception се повтарят.
 * maxRetries=2 означава 2 повторения СЛЕД първия опит във Framework 7.
 */
@Service
public class ResilientCatalogClient {

    private final FlakyCatalogDependency dependency;

    public ResilientCatalogClient(FlakyCatalogDependency dependency) {
        this.dependency = dependency;
    }

    @Retryable(includes = TransientCatalogException.class, maxRetries = 2, delay = 1)
    public String readSafely() {
        return dependency.read();
    }
}
