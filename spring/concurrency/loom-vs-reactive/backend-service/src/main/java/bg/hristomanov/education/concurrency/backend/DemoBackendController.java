package bg.hristomanov.education.concurrency.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Контролируем downstream за MVC/Loom и WebFlux примерите.
 *
 * <p>Този controller не е пример за production API design. Той ни дава
 * предвидими latency и concurrency limits, за да сравняваме orchestration моделите.</p>
 */
@RestController
@RequestMapping("/backend")
public class DemoBackendController {

    private static final int MAX_ITEM_CONCURRENCY = 2;

    private final AtomicInteger concurrentItemRequests =
            new AtomicInteger();

    @GetMapping("/fast")
    String fast() {
        simulateLatency(100);
        return "fast";
    }

    @GetMapping("/slow")
    String slow() {
        simulateLatency(1_500);
        return "slow";
    }

    @GetMapping("/items/{id}")
    ResponseEntity<String> item(@PathVariable int id) {
        int activeRequests =
                concurrentItemRequests.incrementAndGet();

        try {
            if (activeRequests > MAX_ITEM_CONCURRENCY) {
                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Too many concurrent requests");
            }

            simulateLatency(180);
            return ResponseEntity.ok("item-" + id);

        } finally {
            concurrentItemRequests.decrementAndGet();
        }
    }

    private void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Demo backend request was interrupted",
                    e);
        }
    }
}
