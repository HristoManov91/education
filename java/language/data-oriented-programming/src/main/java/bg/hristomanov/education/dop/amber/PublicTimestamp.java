package bg.hristomanov.education.dop.amber;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Java 25 стабилен вариант на "public logical shape != private storage".
 * Клиентите виждат UTC текст, но реализацията държи epochMillis.
 *
 * Това НЕ е custom deconstruction pattern (засега Amber proposal),
 * нито Java Serializable форма, нито ORM entity.
 */
public final class PublicTimestamp {

    private final long epochMillis;

    public PublicTimestamp(String timestamp) {
        Objects.requireNonNull(timestamp, "timestamp");
        // Contract: public ISO timestamp, stored precisely as epoch milliseconds.
        // Normalize parsing failures so callers see a consistent validation API.
        final Instant parsed;
        try {
            parsed = Instant.parse(timestamp);
        } catch (DateTimeParseException invalidFormat) {
            throw new IllegalArgumentException("Expected ISO-8601 UTC timestamp", invalidFormat);
        }
        // Instant.toEpochMilli() truncates sub-millisecond precision.
        // Reject instead of silently losing state during reconstruction.
        if (parsed.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("Sub-millisecond precision is unsupported");
        }
        final long millis;
        try {
            millis = parsed.toEpochMilli();
        } catch (ArithmeticException outOfRange) {
            throw new IllegalArgumentException("Timestamp is outside epoch-millisecond range", outOfRange);
        }
        if (millis < 0) {
            throw new IllegalArgumentException("Timestamp must not precede Unix epoch");
        }
        this.epochMillis = millis;
    }

    public String utcText() {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    public PublicTimestamp withUtcText(String updatedTimestamp) {
        // Минаваме отново през public constructor и неговия invariant;
        // не копираме private field чрез reflection или клониране.
        return new PublicTimestamp(updatedTimestamp);
    }
}
