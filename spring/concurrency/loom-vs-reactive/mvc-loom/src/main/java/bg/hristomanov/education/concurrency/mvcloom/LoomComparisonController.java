package bg.hristomanov.education.concurrency.mvcloom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Stream;

/**
 * WebMVC + Virtual Threads + Structured Concurrency вариантът.
 *
 * <p>Кодът остава synchronous/blocking, но independent operations могат да бъдат
 * организирани като structured child tasks.</p>
 */
@RestController
@RequestMapping("/compare")
public class LoomComparisonController {

    private static final Duration GLOBAL_TIMEOUT =
            Duration.ofMillis(500);

    private final RestClient restClient;

    public LoomComparisonController(
            RestClient.Builder builder,
            @Value("${backend.base-url}") String backendBaseUrl) {

        this.restClient = builder
                .baseUrl(backendBaseUrl)
                .build();
    }

    @GetMapping("/fan-out")
    String fanOut() throws InterruptedException {
        StructuredTaskScope.Joiner<String, Void> joiner =
                StructuredTaskScope.Joiner
                        .awaitAllSuccessfulOrThrow();

        try (StructuredTaskScope<String, Void> scope =
                     StructuredTaskScope.open(joiner)) {

            StructuredTaskScope.Subtask<String> fast =
                    scope.fork(() -> fetch("/backend/fast"));

            StructuredTaskScope.Subtask<String> slow =
                    scope.fork(() -> fetch("/backend/slow"));

            scope.join();

            return fast.get() + " + " + slow.get();
        }
    }

    @GetMapping("/global-timeout")
    ResponseEntity<String> globalTimeout()
            throws InterruptedException {

        StructuredTaskScope.Joiner<String, Void> joiner =
                StructuredTaskScope.Joiner
                        .awaitAllSuccessfulOrThrow();

        try (StructuredTaskScope<String, Void> scope =
                     StructuredTaskScope.open(
                             joiner,
                             configuration -> configuration
                                     .withTimeout(GLOBAL_TIMEOUT))) {

            scope.fork(() -> fetch("/backend/fast"));
            scope.fork(() -> fetch("/backend/slow"));

            scope.join();

            return ResponseEntity.ok(
                    "Both calls completed inside the budget");

        } catch (StructuredTaskScope.TimeoutException e) {
            return ResponseEntity
                    .status(HttpStatus.GATEWAY_TIMEOUT)
                    .body("Global structured timeout after "
                            + GLOBAL_TIMEOUT.toMillis() + " ms");
        }
    }

    @GetMapping("/bounded")
    String boundedConcurrency()
            throws InterruptedException {

        Semaphore downstreamPermits = new Semaphore(2);

        StructuredTaskScope.Joiner<String,
                Stream<StructuredTaskScope.Subtask<String>>> joiner =
                StructuredTaskScope.Joiner
                        .<String>allSuccessfulOrThrow();

        try (StructuredTaskScope<String,
                Stream<StructuredTaskScope.Subtask<String>>> scope =
                     StructuredTaskScope.open(joiner)) {

            for (int item = 0; item < 10; item++) {
                int currentItem = item;

                scope.fork(() -> {
                    downstreamPermits.acquire();

                    try {
                        return fetch(
                                "/backend/items/" + currentItem);
                    } finally {
                        downstreamPermits.release();
                    }
                });
            }

            List<String> results = scope.join()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();

            return String.join(", ", results);
        }
    }

    private String fetch(String path) {
        return restClient.get()
                .uri(path)
                .retrieve()
                .body(String.class);
    }
}
