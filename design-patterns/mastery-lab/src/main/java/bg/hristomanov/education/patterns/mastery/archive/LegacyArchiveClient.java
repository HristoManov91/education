package bg.hristomanov.education.patterns.mastery.archive;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Simulates an incompatible third-party API using bytes and a legacy key scheme. */
public final class LegacyArchiveClient {
    private final ConcurrentMap<String, byte[]> stored = new ConcurrentHashMap<>();

    public String saveLegacy(String key, byte[] payload) {
        if (key == null || payload == null) {
            throw new IllegalArgumentException("invalid legacy archive request");
        }
        if (stored.putIfAbsent(key, Arrays.copyOf(payload, payload.length)) != null) {
            throw new IllegalStateException("duplicate legacy key: " + key);
        }
        return "LEGACY:" + key;
    }

    public String readLegacyText(String key) {
        byte[] storedBytes = stored.get(key);
        return storedBytes == null ? null : new String(storedBytes, StandardCharsets.UTF_8);
    }
}
