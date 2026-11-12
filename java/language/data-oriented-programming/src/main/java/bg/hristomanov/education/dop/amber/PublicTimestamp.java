package bg.hristomanov.education.dop.amber;

import java.time.Instant;
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
        Instant parsed = Instant.parse(timestamp);
        long millis = parsed.toEpochMilli();
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
