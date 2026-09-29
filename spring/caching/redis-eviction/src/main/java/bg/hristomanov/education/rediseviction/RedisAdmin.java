package bg.hristomanov.education.rediseviction;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Component
public class RedisAdmin {

    private final RedisConnectionFactory connectionFactory;

    public RedisAdmin(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void flushDatabase() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    public void setConfig(String name, String value) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().setConfig(name, value);
        }
    }

    public String getConfig(String name) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            Properties properties =
                    connection.serverCommands().getConfig(name);
            return properties.getProperty(name);
        }
    }

    public long usedMemoryBytes() {
        return infoLong("memory", "used_memory");
    }

    public long evictedKeys() {
        return infoLong("stats", "evicted_keys");
    }

    public long databaseSize() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            Long size = connection.serverCommands().dbSize();
            return size == null ? 0L : size;
        }
    }

    public void resetStatistics() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().resetConfigStats();
        }
    }

    public long objectFrequency(String key) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            byte[] script = bytes(
                    "return redis.call('OBJECT','FREQ',KEYS[1])"
            );

            Object result = connection
                    .scriptingCommands()
                    .eval(
                            script,
                            ReturnType.INTEGER,
                            1,
                            bytes(key)
                    );

            if (result instanceof Number number) {
                return number.longValue();
            }

            throw new IllegalStateException(
                    "Unexpected OBJECT FREQ result: " + result
            );
        }
    }

    public boolean ping() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            return "PONG".equalsIgnoreCase(response);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private long infoLong(
            String section,
            String property
    ) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            Properties info =
                    connection.serverCommands().info(section);
            String value = info.getProperty(property);

            if (value == null) {
                throw new IllegalStateException(
                        "Missing Redis INFO property: " + property
                );
            }

            return Long.parseLong(value);
        }
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
