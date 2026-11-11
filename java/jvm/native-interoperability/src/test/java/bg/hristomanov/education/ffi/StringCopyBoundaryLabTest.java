package bg.hristomanov.education.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringCopyBoundaryLabTest {

    @Test
    void javaUtf16UnitsAndUtf8CStringBytesAreDifferentUnits() {
        String text = "A😊";
        assertEquals(3, text.length());
        assertEquals(5, StringCopyBoundaryLab.utf8PayloadLength(text));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeCString = StringCopyBoundaryLab.copyToNativeCString(arena, text);
            assertEquals(6, nativeCString.byteSize()); // UTF-8 + one NUL terminator
            assertArrayEquals(text.getBytes(StandardCharsets.UTF_8),
                    nativeCString.asSlice(0, 5).toArray(ValueLayout.JAVA_BYTE));
            assertEquals(0, nativeCString.get(ValueLayout.JAVA_BYTE, 5));
        }
    }

    @Test
    void changingTheNativeCopyCannotMutateTheOriginalString() {
        String original = "immutable";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment copy = StringCopyBoundaryLab.copyToNativeCString(arena, original);
            copy.set(ValueLayout.JAVA_BYTE, 0, (byte) 'X');
            assertEquals("immutable", original);
            assertNotEquals((byte) 'i', copy.get(ValueLayout.JAVA_BYTE, 0));
            assertEquals((byte) 'X', copy.get(ValueLayout.JAVA_BYTE, 0));
        }
    }

    @Test
    void invalidInputAndEmbeddedNullAreRejectedBeforeAnyNativeCall() {
        try (Arena arena = Arena.ofConfined()) {
            assertThrows(NullPointerException.class,
                    () -> StringCopyBoundaryLab.copyToNativeCString(arena, null));
            assertThrows(NullPointerException.class,
                    () -> StringCopyBoundaryLab.copyToNativeCString(null, "x"));
            assertThrows(IllegalArgumentException.class,
                    () -> StringCopyBoundaryLab.copyToNativeCString(arena, "a\0b"));
        }
    }

    @Test
    void closedNativeMemoryIsNeverSafeToReuse() {
        MemorySegment stale;
        try (Arena arena = Arena.ofConfined()) {
            stale = StringCopyBoundaryLab.copyToNativeCString(arena, "hello");
        }
        assertThrows(IllegalStateException.class,
                () -> stale.get(ValueLayout.JAVA_BYTE, 0));
    }
}
