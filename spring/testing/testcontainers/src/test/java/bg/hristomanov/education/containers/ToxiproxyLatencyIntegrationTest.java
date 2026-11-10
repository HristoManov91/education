package bg.hristomanov.education.containers;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Docker network fault injection; no Spring context or mocking of network behavior. */
@Testcontainers(disabledWithoutDocker = true)
class ToxiproxyLatencyIntegrationTest {
    @Test
    @Timeout(180)
    void delayedDownstreamResponseIsObservedThroughRealTcpProxy() throws Exception {
        try (ToxiproxyScenario scenario = new ToxiproxyScenario()) {
            scenario.start();
            scenario.proxy().toxics().latency("delayed-response", ToxicDirection.DOWNSTREAM, 650);
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(scenario.hostUrl()))
                        .timeout(Duration.ofSeconds(10)).GET().build();

                long started = System.nanoTime();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("nginx");
                // Lower bound checks that traffic actually passed through the configured delay.
                // There is deliberately no fragile assertion on "how much faster" a different JDK is.
                assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                scenario.proxy().toxics().get("delayed-response").remove();
            }
        }
    }
}
