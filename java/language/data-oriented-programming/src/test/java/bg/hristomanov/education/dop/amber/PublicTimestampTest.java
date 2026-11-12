package bg.hristomanov.education.dop.amber;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    void factoryAndReconstructionBothRejectInvalidLogicalInput() {
        assertThrows(IllegalArgumentException.class,
                () -> new PublicTimestamp("1969-12-31T23:59:59Z"));
        assertThrows(IllegalArgumentException.class,
                () -> new PublicTimestamp("not-an-ISO-time"));
        PublicTimestamp valid = new PublicTimestamp("2026-11-12T06:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> valid.withUtcText("1969-12-31T23:59:59Z"));
        assertEquals("2026-11-12T06:00:00Z", valid.utcText());
    }
}
