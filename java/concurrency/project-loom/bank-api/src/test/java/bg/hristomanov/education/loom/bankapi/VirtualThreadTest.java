package bg.hristomanov.education.loom.bankapi;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadTest {

    @Test
    void javaCanRunAUnitOfWorkOnAVirtualThread() throws InterruptedException {
        var observedVirtualThread = new AtomicBoolean(false);

        var thread = Thread.startVirtualThread(
                () -> observedVirtualThread.set(Thread.currentThread().isVirtual()));
        thread.join();

        assertTrue(observedVirtualThread.get());
    }
}
