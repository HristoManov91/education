package bg.hristomanov.education.loom.labs.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ThreadLocalContextBridgeTest {

    @AfterEach
    void clearParent() {
        ThreadLocalContextBridge.clear();
    }

    @Test
    void plainVirtualThreadDoesNotInheritOrdinaryThreadLocal() throws InterruptedException {
        ThreadLocalContextBridge.set(Map.of("requestId", "request-1"));
        AtomicReference<Map<String, String>> observed = new AtomicReference<>();
        Thread thread = Thread.ofVirtual().start(() ->
                observed.set(ThreadLocalContextBridge.current()));
        thread.join();

        assertNull(observed.get());
        assertEquals("request-1", ThreadLocalContextBridge.current().get("requestId"));
    }

    @Test
    void contextFactoryCapturesSnapshotPerTaskSubmission() throws InterruptedException {
        ThreadFactory factory = ThreadLocalContextBridge.virtualThreadsWithContext();
        ThreadLocalContextBridge.set(Map.of("requestId", "first"));
        AtomicReference<Map<String, String>> a = new AtomicReference<>();
        Thread first = factory.newThread(() -> a.set(ThreadLocalContextBridge.current()));

        ThreadLocalContextBridge.set(Map.of("requestId", "second"));
        AtomicReference<Map<String, String>> b = new AtomicReference<>();
        Thread second = factory.newThread(() -> b.set(ThreadLocalContextBridge.current()));
        first.start();
        second.start();
        first.join();
        second.join();

        assertEquals("first", a.get().get("requestId"));
        assertEquals("second", b.get().get("requestId"));
    }

    @Test
    void childCleanupDoesNotMutateParentContextEvenAfterFailure() throws InterruptedException {
        ThreadLocalContextBridge.set(Map.of("requestId", "parent"));
        ThreadFactory factory = ThreadLocalContextBridge.virtualThreadsWithContext();
        AtomicReference<Map<String, String>> observed = new AtomicReference<>();

        Thread child = factory.newThread(() -> {
            assertEquals("parent", ThreadLocalContextBridge.current().get("requestId"));
            ThreadLocalContextBridge.set(Map.of("requestId", "child"));
            observed.set(ThreadLocalContextBridge.current());
            // Runnable ends while context has been modified by legacy code.
            // The ThreadFactory wrapper clears/restores in finally.
        });
        child.start();
        child.join();

        assertEquals("child", observed.get().get("requestId"));
        assertEquals("parent", ThreadLocalContextBridge.current().get("requestId"));
    }

    @Test
    void absentContextIsNotReplacedWithOldCapturedValue() throws InterruptedException {
        ThreadLocalContextBridge.clear();
        ThreadFactory factory = ThreadLocalContextBridge.virtualThreadsWithContext();
        AtomicReference<Map<String, String>> observed = new AtomicReference<>();
        Thread child = factory.newThread(() -> observed.set(ThreadLocalContextBridge.current()));
        child.start();
        child.join();
        assertNull(observed.get());
    }
}
