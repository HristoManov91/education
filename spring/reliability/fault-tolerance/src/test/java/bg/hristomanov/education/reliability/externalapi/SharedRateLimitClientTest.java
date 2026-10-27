package bg.hristomanov.education.reliability.externalapi;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedRateLimitClientTest {

    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void subsequentWorkerSkipsRemoteButOtherTenantAndCredentialContinue() {
        FakeStore sharedStore = new FakeStore();
        SharedRateLimitClient firstWorker = client(sharedStore);
        SharedRateLimitClient secondWorker = client(sharedStore);
        SharedRateLimitClient.Scope blocked =
                SharedRateLimitClient.Scope.of("github", "tenant-1", "secret-1");

        AtomicInteger remoteCalls = new AtomicInteger();
        SharedRateLimitClient.Reply first = firstWorker.call(blocked, () -> {
            remoteCalls.incrementAndGet();
            return SharedRateLimitClient.Reply.remote(429, "rate limited",
                    Map.of("Retry-After", "60"));
        });
        SharedRateLimitClient.Reply second = secondWorker.call(blocked, () -> {
            remoteCalls.incrementAndGet();
            return SharedRateLimitClient.Reply.remote(200, "should not run", Map.of());
        });

        assertThat(first.localShortCircuit()).isFalse();
        assertThat(second.status()).isEqualTo(429);
        assertThat(second.localShortCircuit()).isTrue();
        assertThat(second.header("Retry-After")).isEqualTo("60");
        assertThat(remoteCalls).hasValue(1);

        SharedRateLimitClient.Scope otherTenant =
                SharedRateLimitClient.Scope.of("github", "tenant-2", "secret-1");
        SharedRateLimitClient.Scope otherCredential =
                SharedRateLimitClient.Scope.of("github", "tenant-1", "secret-2");
        firstWorker.call(otherTenant, () -> {
            remoteCalls.incrementAndGet();
            return SharedRateLimitClient.Reply.remote(200, "ok", Map.of());
        });
        secondWorker.call(otherCredential, () -> {
            remoteCalls.incrementAndGet();
            return SharedRateLimitClient.Reply.remote(200, "ok", Map.of());
        });

        assertThat(remoteCalls).hasValue(3);
        assertThat(blocked.redisKey()).doesNotContain("secret-1", "tenant-1");
        assertThat(blocked.toString()).doesNotContain("secret-1");
        assertThatThrownBy(() -> SharedRateLimitClient.Scope.of("github", "tenant", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsDeltaSecondsHttpDateFallbackAndSafetyMaximum() {
        SharedRateLimitClient client = client(new FakeStore());
        assertThat(client.retryAfter("45")).isEqualTo(Duration.ofSeconds(45));
        assertThat(client.retryAfter("Tue, 27 Oct 2026 12:02:00 GMT"))
                .isEqualTo(Duration.ofMinutes(2));
        assertThat(client.retryAfter("1800")).isEqualTo(Duration.ofMinutes(10));
        assertThat(client.retryAfter("invalid")).isEqualTo(Duration.ofSeconds(5));
        assertThat(client.retryAfter(null)).isEqualTo(Duration.ofSeconds(5));
        assertThat(client.retryAfter("0")).isEqualTo(Duration.ofSeconds(5));
        assertThat(client.retryAfter("-15")).isEqualTo(Duration.ofSeconds(5));
    }

    private static SharedRateLimitClient client(SharedRateLimitClient.CooldownStore store) {
        return new SharedRateLimitClient(store, FIXED, Duration.ofSeconds(5), Duration.ofMinutes(10));
    }

    private static final class FakeStore implements SharedRateLimitClient.CooldownStore {
        private final Map<String, Duration> cooldowns = new HashMap<>();

        @Override
        public Duration remaining(SharedRateLimitClient.Scope scope) {
            return cooldowns.getOrDefault(scope.redisKey(), Duration.ZERO);
        }

        @Override
        public void extendIfLonger(SharedRateLimitClient.Scope scope, Duration cooldown) {
            cooldowns.merge(scope.redisKey(), cooldown, (oldValue, newValue) ->
                    oldValue.compareTo(newValue) >= 0 ? oldValue : newValue);
        }
    }
}
