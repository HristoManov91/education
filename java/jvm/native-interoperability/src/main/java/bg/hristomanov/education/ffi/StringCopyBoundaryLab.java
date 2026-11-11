package bg.hristomanov.education.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * UTF-8/C-string encoding is an explicit copy into native-owned memory.
 * This DOES NOT expose the backing representation of a Java String.
 */
public final class StringCopyBoundaryLab {

    private StringCopyBoundaryLab() {
    }

    public static MemorySegment copyToNativeCString(Arena owner, String text) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(text, "text");
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Embedded NUL would truncate a C string");
        }
        // Arena.ofConfined() makes the lifetime/thread ownership explicit.
        return owner.allocateFrom(text);
    }

    public static int utf8PayloadLength(String text) {
        Objects.requireNonNull(text, "text");
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
