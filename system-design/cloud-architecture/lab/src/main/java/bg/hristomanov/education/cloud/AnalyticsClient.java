package bg.hristomanov.education.cloud;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class AnalyticsClient {
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;

    public AnalyticsClient(HttpClient client, URI endpoint, Duration timeout) {
        this.client = Objects.requireNonNull(client);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Positive timeout required");
        }
    }

    public record Report(int count, Instant asOf) {
        public Report {
            Objects.requireNonNull(asOf);
            if (count < 0) throw new IllegalArgumentException("Negative count");
        }
    }

    public static final class RemoteUnavailable extends RuntimeException {
        public RemoteUnavailable(String message) { super(message); }
        public RemoteUnavailable(String message, Throwable cause) { super(message, cause); }
    }

    public Report read() {
        var request = HttpRequest.newBuilder(endpoint).timeout(timeout).GET().build();
        try {
            // Input format deliberately tiny for the local lab: count,ISO-8601 source timestamp.
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                throw new RemoteUnavailable("Analytics HTTP " + status);
            }
            if (status != 200) {
                // Never mask an authorization/contract error as a transient failure.
                throw new IllegalStateException("Unexpected analytics HTTP " + status);
            }
            var fields = response.body().strip().split(",", -1);
            if (fields.length != 2) throw new IllegalArgumentException("Invalid report contract");
            return new Report(Integer.parseInt(fields[0]), Instant.parse(fields[1]));
        } catch (IOException failure) {
            throw new RemoteUnavailable("Analytics transport failure", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted", interrupted);
        }
    }
}
