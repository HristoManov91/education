package bg.hristomanov.education.jfr.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверява реалния HTTP boundary, включително Spring MVC routing,
 * сериализация и превод на validation грешки към HTTP 400.
 * Директен unit test на controller метода не доказва тези неща.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuntimeAreaHttpIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void validRequestReturnsSameShapeCountAndResultOverHttp() throws Exception {
        HttpResponse<String> response = get("/api/jfr/runtime/areas?shapeTypes=2&rounds=5");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "\"shapeTypes\":2",
                "\"rounds\":5",
                "\"evaluatedShapes\":10",
                "\"doubledAreaSum\":");
    }

    @Test
    void invalidWorkloadIsRejectedAsBadRequestOverHttp() throws Exception {
        HttpResponse<String> invalidType = get("/api/jfr/runtime/areas?shapeTypes=0&rounds=1");
        HttpResponse<String> invalidRounds = get("/api/jfr/runtime/areas?shapeTypes=1&rounds=100001");

        assertThat(invalidType.statusCode()).isEqualTo(400);
        assertThat(invalidRounds.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
