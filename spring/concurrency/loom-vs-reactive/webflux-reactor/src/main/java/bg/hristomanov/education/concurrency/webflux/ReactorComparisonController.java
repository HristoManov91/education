package bg.hristomanov.education.concurrency.webflux;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * WebFlux/Reactor вариантът на същите orchestration задачи.
 *
 * <p>Тук concurrency, timeout и stream semantics се моделират чрез operators
 * върху Mono/Flux, а не чрез blocking child threads.</p>
 */
@RestController
@RequestMapping("/compare")
public class ReactorComparisonController {

    private static final Duration GLOBAL_TIMEOUT =
            Duration.ofMillis(500);

    private final WebClient webClient;

    public ReactorComparisonController(
            WebClient.Builder builder,
            @Value("${backend.base-url}") String backendBaseUrl) {

        this.webClient = builder
                .baseUrl(backendBaseUrl)
                .build();
    }

    @GetMapping("/fan-out")
    Mono<String> fanOut() {
        Mono<String> fast = fetch("/backend/fast");
        Mono<String> slow = fetch("/backend/slow");

        return Mono.zip(
                fast,
                slow,
                (fastResult, slowResult) ->
                        fastResult + " + " + slowResult);
    }

    @GetMapping("/global-timeout")
    Mono<String> globalTimeout() {
        return Mono.zip(
                        fetch("/backend/fast"),
                        fetch("/backend/slow"),
                        (fastResult, slowResult) ->
                                fastResult + " + " + slowResult)
                .timeout(GLOBAL_TIMEOUT)
                .onErrorResume(
                        TimeoutException.class,
                        exception -> Mono.just(
                                "Global reactive timeout after "
                                        + GLOBAL_TIMEOUT.toMillis()
                                        + " ms"));
    }

    @GetMapping("/bounded")
    Mono<String> boundedConcurrency() {
        /*
         * Вторият argument на flatMap е max concurrency.
         * Това е естествен reactive начин да ограничим едновременните downstream calls.
         */
        return Flux.range(0, 10)
                .flatMap(
                        this::fetchItem,
                        2)
                .collectList()
                .map(results -> String.join(", ", results));
    }

    @GetMapping(
            value = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> infiniteEvents() {
        /*
         * Тук няма естествен "join point".
         * Stream-ът може да живее, докато client-ът е свързан.
         */
        return Flux.interval(Duration.ofSeconds(1))
                .map(sequence -> "event-" + sequence);
    }

    private Mono<String> fetch(String path) {
        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class);
    }

    private Mono<String> fetchItem(int item) {
        return fetch("/backend/items/" + item);
    }
}
