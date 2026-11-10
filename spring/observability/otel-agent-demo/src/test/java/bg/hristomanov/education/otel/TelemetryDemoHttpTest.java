package bg.hristomanov.education.otel;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Реален HTTP inbound + HTTP outbound, без Redis/Docker.
 * Няма твърдение, че този тест проверява OTel telemetry без OTel javaagent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.redis.enabled=false")
class TelemetryDemoHttpTest {

    private static final AtomicInteger upstreamRequests = new AtomicInteger();
    private static final HttpServer upstream = createFakeUpstream();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("lab.price-base-url",
                () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @Test
    void requestTraversesHttpBoundaryAndPreservesPriceContract() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/quotes/widget")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"sku\":\"widget\""), response.body());
        assertTrue(response.body().contains("\"totalCents\":2500"), response.body());
        assertTrue(response.body().contains("\"cached\":false"), response.body());
        assertEquals(1, upstreamRequests.get(),
                "Default path must really invoke upstream HTTP");
    }

    @AfterAll
    static void shutdown() {
        upstream.stop(0);
    }

    private static HttpServer createFakeUpstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/prices/widget", exchange -> {
                upstreamRequests.incrementAndGet();
                byte[] body = "{\"sku\":\"widget\",\"unitPriceCents\":1250}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (java.io.OutputStream stream = exchange.getResponseBody()) {
                    stream.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot start local fake upstream", error);
        }
    }
}
