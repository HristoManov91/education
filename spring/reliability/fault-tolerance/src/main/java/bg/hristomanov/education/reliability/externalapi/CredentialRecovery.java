package bg.hristomanov.education.reliability.externalapi;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Изтекъл/отнет token е operational incident, не transient HTTP failure.
 * Блокираме само ПОТВЪРДЕН provider-specific "invalid credential" резултат.
 * Generic 401/403 сами по себе си НЕ доказват, че token-ът е непоправим.
 */
public final class CredentialRecovery {

    public record ConnectionId(String provider, String tenantId, String integrationId) {
        public ConnectionId {
            if (provider == null || provider.isBlank()
                    || tenantId == null || tenantId.isBlank()
                    || integrationId == null || integrationId.isBlank()) {
                throw new IllegalArgumentException("Provider, tenant and integration ID required");
            }
        }
    }

    public interface SuspensionStore {
        /** Atomic: true САМО при първа transition към suspended. */
        boolean suspend(ConnectionId id);
        boolean suspended(ConnectionId id);
        void restore(ConnectionId id);
    }

    @FunctionalInterface
    public interface ActionRequiredNotifier {
        void notifyReauthorizationRequired(ConnectionId id);
    }

    private final SuspensionStore store;
    private final Predicate<SharedRateLimitClient.Reply> providerSpecificInvalidToken;
    private final ActionRequiredNotifier notifier;

    public CredentialRecovery(
            SuspensionStore store,
            Predicate<SharedRateLimitClient.Reply> providerSpecificInvalidToken,
            ActionRequiredNotifier notifier
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.providerSpecificInvalidToken = Objects.requireNonNull(
                providerSpecificInvalidToken, "providerSpecificInvalidToken");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public SharedRateLimitClient.Reply call(
            ConnectionId connection, Supplier<SharedRateLimitClient.Reply> remote
    ) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(remote, "remote");
        if (store.suspended(connection)) {
            // Отговорът е explicit degraded state, не фалшив success.
            return new SharedRateLimitClient.Reply(503,
                    "Integration paused: credentials require manual repair",
                    java.util.Map.of(), true);
        }

        SharedRateLimitClient.Reply response = Objects.requireNonNull(remote.get(), "response");
        if (providerSpecificInvalidToken.test(response) && store.suspend(connection)) {
            // Production: атомарен Outbox за reliable notification.
            // Тук демонстрираме one-shot transition; notification failures се виждат.
            notifier.notifyReauthorizationRequired(connection);
        }
        return response;
    }

    /**
     * Извиква се САМО след проверено обновяване на credentials.
     * Не отключвай автоматично по таймер или само защото получиш random 200.
     */
    public void credentialsReplacedAndVerified(ConnectionId connection) {
        store.restore(connection);
    }
}
