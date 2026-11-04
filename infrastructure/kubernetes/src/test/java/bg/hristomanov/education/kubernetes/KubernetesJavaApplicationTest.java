package bg.hristomanov.education.kubernetes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KubernetesJavaApplicationTest {
    @Autowired
    private Environment environment;

    @Test
    void realHttpEndpointAndActuatorHealthWorkWithoutACluster() throws Exception {
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            HttpResponse<String> hello = get(client, base + "/api/hello");
            assertThat(hello.statusCode()).isEqualTo(200);
            assertThat(hello.body()).contains("Hello from Kubernetes", "javaFeature");
            HttpResponse<String> work = get(client, base + "/api/work?delayMs=0");
            assertThat(work.statusCode()).isEqualTo(200);
            assertThat(work.body()).contains("completed");
            HttpResponse<String> health = get(client, base + "/actuator/health/liveness");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("UP");
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
