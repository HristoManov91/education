package bg.hristomanov.education.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Един и същ API contract на JDK 25 и JDK 28 EA: confined arena,
 * zero-initialized native memory, explicit lifetime.
 * В JDK 28 имплементацията може да използва per-thread/carrier pools.
 */
public final class ConfinedArenaAllocationLab {

    private ConfinedArenaAllocationLab() {
    }

    public static int allocateWriteRead(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("bytes must be positive");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bytes, 1);
            int lastOffset = bytes - 1;
            if (segment.get(ValueLayout.JAVA_BYTE, lastOffset) != 0) {
                throw new IllegalStateException("Fresh arena allocation must be zero-initialized");
            }
            segment.set(ValueLayout.JAVA_BYTE, lastOffset, (byte) 90);
            return Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, lastOffset));
        }
    }
}
