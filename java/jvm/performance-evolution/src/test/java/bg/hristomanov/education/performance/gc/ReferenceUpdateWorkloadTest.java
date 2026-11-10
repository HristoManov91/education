package bg.hristomanov.education.performance.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReferenceUpdateWorkloadTest {

    @Test
    void sameInputProducesIdenticalBusinessChecksum() {
        assertEquals(ReferenceUpdateWorkload.run(73, 10_000, 923),
                ReferenceUpdateWorkload.run(73, 10_000, 923));
        assertEquals(-10L, ReferenceUpdateWorkload.run(10, 0, 923));
    }

    @Test
    void rejectsInvalidDimensionsInsteadOfSilentlyRunningAnotherWorkload() {
        assertThrows(IllegalArgumentException.class,
                () -> ReferenceUpdateWorkload.run(1, 100, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ReferenceUpdateWorkload.run(32, -1, 1));
    }
}
