package bg.hristomanov.education.aitesting.api;

import bg.hristomanov.education.aitesting.TestingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True socket-level integration: embedded server + security filter chain
 * + validation + serialization + domain. No @WithMockUser shortcut:
 * the HTTP client and servlet request run in separate threads.
 */
@SpringBootTest(classes = TestingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CustomerHttpIT.TestUsers.class)
class CustomerHttpIT {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    void realHttpRejectsAnonymousAndAcceptsActualBasicAuthentication() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + "/api/customers");
        String email = "verified-" + java.util.UUID.randomUUID() + "@example.com";
        String requestJson = """
                {"email":"%s","birthDate":"1990-01-01","acceptedTerms":true}
                """.formatted(email);

        HttpResponse<String> unauthorized = send(uri, requestJson, null);
        assertThat(unauthorized.statusCode()).isEqualTo(401);

        HttpResponse<String> forbidden = send(uri, requestJson, credentials("viewer", "test-password"));
        assertThat(forbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> created = send(uri, requestJson, credentials("staff", "test-password"));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).isPresent();
        assertThat(created.body()).contains(email).contains("registeredAt").contains("id");

        URI createdUri = uri.resolve(created.headers().firstValue("Location").orElseThrow());
        HttpRequest read = HttpRequest.newBuilder(createdUri)
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", credentials("staff", "test-password"))
                .GET().build();

        HttpResponse<String> fetched = http.send(read, HttpResponse.BodyHandlers.ofString());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains(email);
    }

    @Test
    void invalidPayloadIsRejectedThroughRealHttpValidation() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + "/api/customers");
        HttpResponse<String> badRequest = send(uri,
                "{\"email\":\"no-at-symbol\",\"birthDate\":\"1990-01-01\",\"acceptedTerms\":true}",
                credentials("staff", "test-password"));
        assertThat(badRequest.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> send(URI uri, String body, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String credentials(String username, String password) {
        String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                token.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestUsers {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("staff").password("{noop}test-password").roles("STAFF").build(),
                    User.withUsername("viewer").password("{noop}test-password").roles("VIEWER").build());
        }
    }
}
