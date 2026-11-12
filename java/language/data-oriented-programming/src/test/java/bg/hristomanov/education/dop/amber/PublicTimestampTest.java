package bg.hristomanov.education.dop.amber;

import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicTimestampTest {

    @Test
    void publicUtcShapeCanDifferFromPrivateLongRepresentation() {
        PublicTimestamp timestamp = new PublicTimestamp("2026-11-12T08:00:00+02:00");
        assertEquals("2026-11-12T06:00:00Z", timestamp.utcText());

        PublicTimestamp updated = timestamp.withUtcText("2026-11-12T07:00:00Z");
        assertEquals("2026-11-12T07:00:00Z", updated.utcText());
        assertEquals("2026-11-12T06:00:00Z", timestamp.utcText());
        assertNotSame(timestamp, updated);
    }

    @Test
    void preservesExactlyRepresentableMillisecondsAndRejectsNanos() {
        PublicTimestamp timestamp =
                new PublicTimestamp("2026-11-12T06:00:00.123Z");
        assertEquals("2026-11-12T06:00:00.123Z", timestamp.utcText());

        IllegalArgumentException precision = assertThrows(IllegalArgumentException.class,
                () -> new PublicTimestamp("2026-11-12T06:00:00.123456789Z"));
        assertTrue(precision.getMessage().contains("Sub-millisecond"));
        assertThrows(IllegalArgumentException.class,
                () -> timestamp.withUtcText("2026-11-12T06:00:00.000001Z"));
        assertEquals("2026-11-12T06:00:00.123Z", timestamp.utcText());
    }

    @Test
    void factoryAndReconstructionBothRejectInvalidLogicalInput() {
        assertThrows(IllegalArgumentException.class,
                () -> new PublicTimestamp("1969-12-31T23:59:59Z"));
        IllegalArgumentException invalidFormat = assertThrows(IllegalArgumentException.class,
                () -> new PublicTimestamp("not-an-ISO-time"));
        assertInstanceOf(DateTimeParseException.class, invalidFormat.getCause());
        assertTrue(invalidFormat.getMessage().contains("ISO-8601"));
        PublicTimestamp valid = new PublicTimestamp("2026-11-12T06:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> valid.withUtcText("1969-12-31T23:59:59Z"));
        assertEquals("2026-11-12T06:00:00Z", valid.utcText());
    }
}
