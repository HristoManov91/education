package bg.hristomanov.education.build;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Models deterministic serialization of release metadata from explicit inputs.
 * The Maven build does not invoke this class; the JAR check runs separately in CI.
 */
public final class ReleaseMetadata {

    private ReleaseMetadata() {
    }

    public static byte[] encode(Map<String, String> attributes) {
        Objects.requireNonNull(attributes, "attributes");

        StringBuilder result = new StringBuilder();
        new TreeMap<>(attributes).forEach((key, value) -> {
            if (!key.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
                throw new IllegalArgumentException("Invalid metadata key: " + key);
            }
            if (value == null || value.contains("\n") || value.contains("\r")) {
                throw new IllegalArgumentException("Invalid metadata value for " + key);
            }

            // Stable key order, UTF-8 and explicit LF avoid machine-specific output.
            result.append(key).append('=').append(value).append('\n');
        });
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
}
