package bg.hristomanov.education.loom.labs.classinit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassInitializationWaitLabTest {

    @Test
    void classInitializerCompletesOnceAndAllVirtualWaitersSeePublishedResult() throws Exception {
        ClassInitializationWaitLab.Result result = ClassInitializationWaitLab.runOnce(8);
        assertEquals(42, result.initialValue());
        assertEquals(8, result.waiters());
        assertEquals(8 * 42, result.sum());
        assertEquals(1, result.initCount());
    }

    @Test
    void emptyWorkloadIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassInitializationWaitLab.runOnce(0));
    }
}
