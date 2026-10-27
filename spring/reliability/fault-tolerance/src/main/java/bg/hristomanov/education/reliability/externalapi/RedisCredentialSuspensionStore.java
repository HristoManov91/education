package bg.hristomanov.education.reliability.externalapi;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Shared suspension flag за много workers. Без TTL: operator/verified
 * credential replacement трябва изрично да изчисти pause-а.
 * Redis alone не гарантира durable notification — за това използвай Outbox.
 */
public final class RedisCredentialSuspensionStore
        implements CredentialRecovery.SuspensionStore {

    private final StringRedisTemplate redis;

    public RedisCredentialSuspensionStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public boolean suspend(CredentialRecovery.ConnectionId connection) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(connection), "ACTION_REQUIRED"));
    }

    @Override
    public boolean suspended(CredentialRecovery.ConnectionId connection) {
        return Boolean.TRUE.equals(redis.hasKey(key(connection)));
    }

    @Override
    public void restore(CredentialRecovery.ConnectionId connection) {
        redis.delete(key(connection));
    }

    private String key(CredentialRecovery.ConnectionId id) {
        String input = id.provider().length() + ":" + id.provider()
                + id.tenantId().length() + ":" + id.tenantId()
                + id.integrationId().length() + ":" + id.integrationId();
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return "education:api-resilience:credential:" + HexFormat.of()
                    .formatHex(sha256.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
