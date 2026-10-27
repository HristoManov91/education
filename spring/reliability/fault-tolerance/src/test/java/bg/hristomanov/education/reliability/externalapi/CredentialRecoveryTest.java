package bg.hristomanov.education.reliability.externalapi;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialRecoveryTest {

    @Test
    void confirmedInvalidTokenPausesOnlyAffectedIntegrationAndNotifiesOnce() {
        MemoryStore store = new MemoryStore();
        AtomicInteger notices = new AtomicInteger();
        CredentialRecovery policy = new CredentialRecovery(store,
                reply -> reply.status() == 401 && "invalid_token".equals(reply.body()),
                id -> notices.incrementAndGet());

        CredentialRecovery.ConnectionId a =
                new CredentialRecovery.ConnectionId("github", "org-a", "conn-1");
        CredentialRecovery.ConnectionId b =
                new CredentialRecovery.ConnectionId("github", "org-b", "conn-1");

        AtomicInteger remoteCalls = new AtomicInteger();
        SharedRateLimitClient.Reply first = policy.call(a, () -> {
            remoteCalls.incrementAndGet();
            return SharedRateLimitClient.Reply.remote(401, "invalid_token", Map.of());
        });
        SharedRateLimitClient.Reply second = policy.call(a, () -> {
            remoteCalls.incrementAndGet();
            return SharedRateLimitClient.Reply.remote(200, "should never run", Map.of());
        });

        assertThat(first.status()).isEqualTo(401);
        assertThat(second.status()).isEqualTo(503);
        assertThat(second.fromSharedCooldown()).isTrue();
        assertThat(remoteCalls).hasValue(1);
        assertThat(notices).hasValue(1);

        assertThat(policy.call(b, () -> SharedRateLimitClient.Reply.remote(
                200, "other customer works", Map.of())).status()).isEqualTo(200);
        assertThat(store.suspended(b)).isFalse();

        policy.credentialsReplacedAndVerified(a);
        assertThat(policy.call(a, () -> SharedRateLimitClient.Reply.remote(
                200, "verified replacement", Map.of())).status()).isEqualTo(200);
    }

    @Test
    void generic401429And5xxAreNotAutomaticallyClassifiedAsDeadCredentials() {
        MemoryStore store = new MemoryStore();
        CredentialRecovery policy = new CredentialRecovery(store,
                reply -> reply.status() == 401 && "invalid_token".equals(reply.body()),
                id -> {
                    throw new AssertionError("no manual notice for transient or ambiguous errors");
                });
        CredentialRecovery.ConnectionId id =
                new CredentialRecovery.ConnectionId("github", "org", "integration");

        for (int status : new int[]{401, 403, 429, 503}) {
            SharedRateLimitClient.Reply reply = policy.call(id, () ->
                    SharedRateLimitClient.Reply.remote(status, "generic failure", Map.of()));
            assertThat(reply.status()).isEqualTo(status);
        }
        assertThat(store.suspended(id)).isFalse();
    }

    private static final class MemoryStore implements CredentialRecovery.SuspensionStore {
        private final Set<CredentialRecovery.ConnectionId> blocked =
                ConcurrentHashMap.newKeySet();

        @Override
        public boolean suspend(CredentialRecovery.ConnectionId id) {
            return blocked.add(id);
        }

        @Override
        public boolean suspended(CredentialRecovery.ConnectionId id) {
            return blocked.contains(id);
        }

        @Override
        public void restore(CredentialRecovery.ConnectionId id) {
            blocked.remove(id);
        }
    }
}
