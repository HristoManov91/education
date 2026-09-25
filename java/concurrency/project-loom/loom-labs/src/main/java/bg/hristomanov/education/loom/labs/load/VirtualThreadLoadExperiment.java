package bg.hristomanov.education.loom.labs.load;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Educational load experiment за сравнение на един и същ blocking Spring MVC flow
 * с platform threads и с virtual threads.
 *
 * <p>Това НЕ е microbenchmark. Числата зависят от машината, JVM warm-up,
 * останалите процеси и downstream услугата. Търсим поведението на системата
 * под concurrency, а не универсална "X пъти по-бързо" стойност.</p>
 *
 * <p>Load generator-ът използва virtual thread per request, за да не превърнем
 * самия generator в тесния ресурс, който измерваме.</p>
 */
public final class VirtualThreadLoadExperiment {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final int DEFAULT_REQUESTS = 80;
    private static final int DEFAULT_CONCURRENCY = 40;

    private static final String REQUEST_BODY = """
            {
              "customerId": "2b8d8f54-e104-4d21-97de-6ef9a78db392",
              "amount": 25000,
              "purpose": "virtual thread load experiment"
            }
            """;

    private VirtualThreadLoadExperiment() {
    }

    public static void main(String[] args) throws InterruptedException {
        String baseUrl = args.length > 0 ? args[0] : DEFAULT_BASE_URL;
        int requestCount = args.length > 1
                ? Integer.parseInt(args[1])
                : DEFAULT_REQUESTS;
        int concurrency = args.length > 2
                ? Integer.parseInt(args[2])
                : DEFAULT_CONCURRENCY;

        run(baseUrl, requestCount, concurrency);
    }

    static void run(
            String baseUrl,
            int requestCount,
            int concurrency) throws InterruptedException {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        Semaphore concurrencyLimit = new Semaphore(concurrency);
        CountDownLatch finished = new CountDownLatch(requestCount);
        List<RequestResult> results =
                Collections.synchronizedList(new ArrayList<>());

        long experimentStartNanos = System.nanoTime();

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            for (int requestNumber = 1;
                 requestNumber <= requestCount;
                 requestNumber++) {

                int currentRequestNumber = requestNumber;

                executor.submit(() -> executeRequest(
                        httpClient,
                        baseUrl,
                        currentRequestNumber,
                        concurrencyLimit,
                        finished,
                        results));
            }

            finished.await();
        }

        long experimentElapsedMillis =
                Duration.ofNanos(System.nanoTime() - experimentStartNanos)
                        .toMillis();

        printSummary(
                baseUrl,
                requestCount,
                concurrency,
                experimentElapsedMillis,
                results);
    }

    private static void executeRequest(
            HttpClient httpClient,
            String baseUrl,
            int requestNumber,
            Semaphore concurrencyLimit,
            CountDownLatch finished,
            List<RequestResult> results) {

        boolean permitAcquired = false;

        try {
            concurrencyLimit.acquire();
            permitAcquired = true;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/loan-applications"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(REQUEST_BODY))
                    .build();

            long requestStartNanos = System.nanoTime();

            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding());

            long elapsedMillis =
                    Duration.ofNanos(System.nanoTime() - requestStartNanos)
                            .toMillis();

            boolean successful =
                    response.statusCode() >= 200
                            && response.statusCode() < 300;

            String error = successful
                    ? null
                    : "HTTP " + response.statusCode();

            results.add(new RequestResult(
                    requestNumber,
                    successful,
                    elapsedMillis,
                    error));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            results.add(new RequestResult(
                    requestNumber,
                    false,
                    0,
                    "interrupted"));
        } catch (IOException | RuntimeException e) {
            results.add(new RequestResult(
                    requestNumber,
                    false,
                    0,
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
        } finally {
            if (permitAcquired) {
                concurrencyLimit.release();
            }

            finished.countDown();
        }
    }

    private static void printSummary(
            String baseUrl,
            int requestCount,
            int concurrency,
            long experimentElapsedMillis,
            List<RequestResult> results) {

        List<Long> successfulLatencies = results.stream()
                .filter(RequestResult::successful)
                .map(RequestResult::elapsedMillis)
                .sorted()
                .toList();

        long successCount = successfulLatencies.size();
        long failureCount = requestCount - successCount;

        double elapsedSeconds = experimentElapsedMillis / 1000.0;
        double requestsPerSecond = elapsedSeconds == 0
                ? 0
                : requestCount / elapsedSeconds;

        System.out.println();
        System.out.println("=== Virtual Thread Load Experiment ===");
        System.out.println("Target:       " + baseUrl);
        System.out.println("Requests:     " + requestCount);
        System.out.println("Concurrency:  " + concurrency);
        System.out.println("Success:      " + successCount);
        System.out.println("Failures:     " + failureCount);
        System.out.println("Total ms:     " + experimentElapsedMillis);

        System.out.printf(
                Locale.ROOT,
                "Requests/sec: %.2f%n",
                requestsPerSecond);

        if (!successfulLatencies.isEmpty()) {
            System.out.println("p50 ms:       "
                    + percentile(successfulLatencies, 0.50));
            System.out.println("p95 ms:       "
                    + percentile(successfulLatencies, 0.95));
            System.out.println("max ms:       "
                    + successfulLatencies.get(
                    successfulLatencies.size() - 1));
        }

        results.stream()
                .filter(result -> !result.successful())
                .limit(3)
                .forEach(result ->
                        System.out.println(
                                "Failure #" + result.requestNumber()
                                        + ": " + result.error()));
    }

    private static long percentile(
            List<Long> sortedValues,
            double percentile) {

        int index =
                (int) Math.ceil(percentile * sortedValues.size()) - 1;

        int boundedIndex =
                Math.max(0, Math.min(index, sortedValues.size() - 1));

        return sortedValues.get(boundedIndex);
    }

    private record RequestResult(
            int requestNumber,
            boolean successful,
            long elapsedMillis,
            String error) {
    }
}
