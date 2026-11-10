package bg.hristomanov.education.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfinedArenaAllocationLabTest {

    @Test
    void tinyAndLargerAllocationsKeepSameCorrectnessContract() {
        for (int bytes : new int[]{1, 5, 8, 20, 32, 64, 128}) {
            assertEquals(90, ConfinedArenaAllocationLab.allocateWriteRead(bytes));
            assertEquals(90, ConfinedArenaAllocationLab.allocateWriteRead(bytes));
        }
    }

    @Test
    void closedArenaInvalidatesSegmentsEvenWhenJdkMayReuseNativePool() {
        MemorySegment segment;
        try (Arena arena = Arena.ofConfined()) {
            segment = arena.allocate(8);
            segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 7);
        }
        assertThrows(IllegalStateException.class,
                () -> segment.get(ValueLayout.JAVA_BYTE, 0));
    }

    @Test
    void rejectsZeroAndNegativeAllocations() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfinedArenaAllocationLab.allocateWriteRead(0));
        assertThrows(IllegalArgumentException.class,
                () -> ConfinedArenaAllocationLab.allocateWriteRead(-1));
    }
}
