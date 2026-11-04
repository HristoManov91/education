import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Source-launched unchanged on JDK 21 and JDK 25; no package, no Maven Java 25 class-file dependency. */
class PinningProbe {
    public static void main(String[] args) throws Exception {
        URI endpoint = URI.create(args[0]);
        int count = Integer.parseInt(args[1]);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20)).GET().build();
        long started = System.nanoTime();
        List<Future<Integer>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    Object ownMonitor = new Object();
                    // Each worker uses an independent monitor: the goal is carrier pinning,
                    // not serialization on one contended application lock.
                    synchronized (ownMonitor) {
                        Thread.sleep(80);
                        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                    }
                }));
            }
            for (Future<Integer> result : futures) {
                if (result.get() != 200) throw new AssertionError("Unexpected HTTP status");
            }
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        System.out.printf("PINNING_PROBE_OK java=%s count=%d elapsedMs=%d%n",
                Runtime.version(), count, elapsedMs);
    }
}
