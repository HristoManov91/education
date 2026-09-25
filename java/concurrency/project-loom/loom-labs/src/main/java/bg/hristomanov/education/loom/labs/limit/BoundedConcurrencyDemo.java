package bg.hristomanov.education.loom.labs.limit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Stream;

/**
 * Доказва, че virtual threads махат scarcity на threads, но НЕ махат
 * ограниченията на downstream ресурсите.
 *
 * <p>Стартирай първо bank-services. Неговият demo endpoint допуска максимум
 * две едновременни заявки. Unbounded вариантът създава всички subtasks веднага
 * и част от тях получават HTTP 429. Bounded вариантът пак създава virtual thread
 * per task, но Semaphore пази реалния scarce resource.</p>
 */
public final class BoundedConcurrencyDemo {

    private static final String DEFAULT_BASE_URL = "http://localhost:8081";
    private static final int REQUEST_COUNT = 10;
    private static final int MAX_DOWNSTREAM_CONCURRENCY = 2;

    private BoundedConcurrencyDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        String baseUrl = args.length > 0 ? args[0] : DEFAULT_BASE_URL;
        HttpClient httpClient = HttpClient.newHttpClient();

        List<PageResult> unbounded = fetchUnbounded(httpClient, baseUrl);
        print("UNBOUNDED", unbounded);

        List<PageResult> bounded = fetchBounded(httpClient, baseUrl);
        print("BOUNDED", bounded);
    }

    public static List<PageResult> fetchUnbounded(
            HttpClient httpClient,
            String baseUrl) throws InterruptedException {

        StructuredTaskScope.Joiner<PageResult,
                Stream<StructuredTaskScope.Subtask<PageResult>>> joiner =
                StructuredTaskScope.Joiner.<PageResult>allSuccessfulOrThrow();

        try (StructuredTaskScope<PageResult,
                Stream<StructuredTaskScope.Subtask<PageResult>>> scope =
                     StructuredTaskScope.open(joiner)) {

            for (int page = 0; page < REQUEST_COUNT; page++) {
                int currentPage = page;
                scope.fork(() -> fetchPage(httpClient, baseUrl, currentPage));
            }

            return scope.join()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();
        }
    }

    public static List<PageResult> fetchBounded(
            HttpClient httpClient,
            String baseUrl) throws InterruptedException {

        Semaphore downstreamPermits =
                new Semaphore(MAX_DOWNSTREAM_CONCURRENCY);

        StructuredTaskScope.Joiner<PageResult,
                Stream<StructuredTaskScope.Subtask<PageResult>>> joiner =
                StructuredTaskScope.Joiner.<PageResult>allSuccessfulOrThrow();

        try (StructuredTaskScope<PageResult,
                Stream<StructuredTaskScope.Subtask<PageResult>>> scope =
                     StructuredTaskScope.open(joiner)) {

            for (int page = 0; page < REQUEST_COUNT; page++) {
                int currentPage = page;

                scope.fork(() -> {
                    /*
                     * Не правим "pool от 2 virtual threads".
                     * Ограничаваме само секцията, която ползва scarce downstream resource-а.
                     */
                    downstreamPermits.acquire();

                    try {
                        return fetchPage(
                                httpClient,
                                baseUrl,
                                currentPage);
                    } finally {
                        downstreamPermits.release();
                    }
                });
            }

            return scope.join()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();
        }
    }

    private static PageResult fetchPage(
            HttpClient httpClient,
            String baseUrl,
            int page) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/demo/limited/pages/" + page))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        return new PageResult(
                page,
                response.statusCode(),
                response.body());
    }

    private static void print(
            String label,
            List<PageResult> results) {

        long successful = results.stream()
                .filter(result -> result.statusCode() == 200)
                .count();

        long tooManyRequests = results.stream()
                .filter(result -> result.statusCode() == 429)
                .count();

        System.out.println();
        System.out.println("=== " + label + " ===");
        System.out.println("Success: " + successful);
        System.out.println("HTTP 429: " + tooManyRequests);
    }

    public record PageResult(
            int page,
            int statusCode,
            String body) {
    }
}
