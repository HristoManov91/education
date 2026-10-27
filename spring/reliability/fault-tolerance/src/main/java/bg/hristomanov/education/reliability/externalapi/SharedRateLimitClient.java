package bg.hristomanov.education.reliability.externalapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Огражда HTTP client-а с общ cooldown, вместо всеки worker да прави безсмислени
 * retries след 429. Създава синтетичен отговор, но НЕ задържа worker да спи.
 * Този guard не е строг distributed rate limiter: паралелни първи calls могат
 * да минат преди първата 429 да се появи в Redis.
 */
public final class SharedRateLimitClient {

    public interface CooldownStore {
        Duration remaining(Scope scope);
        void extendIfLonger(Scope scope, Duration cooldown);
    }

    /**
     * Държим само SHA-256 ключ, никога суровите credentials.
     * Provider + tenant + credential идентифицират ЕДНА rate-limit квота.
     */
    public static final class Scope {
        private final String redisKey;

        private Scope(String redisKey) {
            this.redisKey = redisKey;
        }

        public static Scope of(String provider, String tenantId, String credential) {
            if (provider == null || provider.isBlank()
                    || tenantId == null || tenantId.isBlank()
                    || credential == null || credential.isBlank()) {
                throw new IllegalArgumentException("Provider, tenant and credential are required");
            }
            String parts = provider.length() + ":" + provider
                    + tenantId.length() + ":" + tenantId
                    + credential.length() + ":" + credential;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String fingerprint = HexFormat.of()
                        .formatHex(digest.digest(parts.getBytes(StandardCharsets.UTF_8)));
                return new Scope("education:api-resilience:429:" + fingerprint);
            } catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("Required SHA-256 digest unavailable", failure);
            }
        }

        public String redisKey() {
            return redisKey;
        }

        @Override
        public String toString() {
            return "Scope[redacted]";
        }
    }

    public record Reply(int status, String body, Map<String, String> headers, boolean localShortCircuit) {
        public Reply {
            headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        }

        public static Reply remote(int status, String body, Map<String, String> headers) {
            return new Reply(status, body, headers, false);
        }

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
        }
    }

    private final CooldownStore store;
    private final Clock clock;
    private final Duration fallback;
    private final Duration maximum;

    public SharedRateLimitClient(CooldownStore store, Clock clock, Duration fallback, Duration maximum) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fallback = positive(fallback, "fallback");
        this.maximum = positive(maximum, "maximum");
        if (fallback.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("fallback must not exceed maximum");
        }
    }

    public Reply call(Scope scope, Supplier<Reply> remote) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(remote, "remote");

        Duration remaining = store.remaining(scope);
        if (remaining.isPositive()) {
            long seconds = Math.max(1, (remaining.toMillis() + 999) / 1_000);
            return new Reply(429, "Shared rate-limit cooldown: remote call skipped",
                    Map.of("Retry-After", Long.toString(seconds)), true);
        }

        Reply response = Objects.requireNonNull(remote.get(), "remote response");
        if (response.status() == 429) {
            // Не кешираме body/headers: може да съдържат потребителски данни.
            store.extendIfLonger(scope, retryAfter(response.header("Retry-After")));
        }
        return response;
    }

    /**
     * Retry-After по RFC 9110: delta-seconds ИЛИ HTTP-date.
     * Невалиден/липсващ header => ограничен fallback, не безкрайно изчакване.
     */
    public Duration retryAfter(String header) {
        if (header == null || header.isBlank()) {
            return fallback;
        }
        String value = header.trim();
        try {
            long seconds = Long.parseLong(value);
            return cap(Duration.ofSeconds(seconds));
        } catch (NumberFormatException ignored) {
            // Може да е RFC 1123 HTTP-date; пробваме втория легален формат.
        }
        try {
            Instant until = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return cap(Duration.between(clock.instant(), until));
        } catch (DateTimeParseException failure) {
            return fallback;
        }
    }

    private Duration cap(Duration requested) {
        if (requested.isNegative() || requested.isZero()) {
            return fallback;
        }
        return requested.compareTo(maximum) > 0 ? maximum : requested;
    }

    private static Duration positive(Duration duration, String field) {
        Objects.requireNonNull(duration, field);
        if (!duration.isPositive()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return duration;
    }
}
