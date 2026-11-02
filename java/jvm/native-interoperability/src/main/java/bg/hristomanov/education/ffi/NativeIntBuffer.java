package bg.hristomanov.education.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Demonstrates bounded native memory with explicit layout and lifetime.
 * This is not expected to be faster than an int[] for a small computation.
 */
public final class NativeIntBuffer {

    private NativeIntBuffer() {
    }

    public static int addExactly(int first, int second) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment values = arena.allocate(2L * ValueLayout.JAVA_INT.byteSize(),
                    ValueLayout.JAVA_INT.byteAlignment());
            values.setAtIndex(ValueLayout.JAVA_INT, 0, first);
            values.setAtIndex(ValueLayout.JAVA_INT, 1, second);
            return Math.addExact(
                    values.getAtIndex(ValueLayout.JAVA_INT, 0),
                    values.getAtIndex(ValueLayout.JAVA_INT, 1));
        }
    }
}
