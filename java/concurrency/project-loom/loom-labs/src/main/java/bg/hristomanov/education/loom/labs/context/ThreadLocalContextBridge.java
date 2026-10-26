package bg.hristomanov.education.loom.labs.context;

import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * Minimal MDC-style context carrier without logging-framework dependencies.
 * Captures a snapshot when each newThread(...) is called.
 *
 * Real Logback/SLF4J integrations use MDC.getCopyOfContextMap /
 * setContextMap / clear with the same try/finally invariant.
 *
 * ScopedValue remains preferable for bounded immutable request data whenever
 * the API supports it; this bridge is for legacy ThreadLocal integrations.
 */
public final class ThreadLocalContextBridge {

    private static final ThreadLocal<Map<String, String>> LOG_CONTEXT = new ThreadLocal<>();

    private ThreadLocalContextBridge() {
    }

    public static void set(Map<String, String> values) {
        LOG_CONTEXT.set(Map.copyOf(values));
    }

    public static Map<String, String> current() {
        return LOG_CONTEXT.get();
    }

    public static void clear() {
        LOG_CONTEXT.remove();
    }

    public static ThreadFactory virtualThreadsWithContext() {
        ThreadFactory delegate = Thread.ofVirtual().factory();
        return task -> {
            // Capture on task submission, not when the factory was created.
            Map<String, String> captured = LOG_CONTEXT.get();
            return delegate.newThread(() -> {
                Map<String, String> previous = LOG_CONTEXT.get();
                try {
                    if (captured == null) {
                        LOG_CONTEXT.remove();
                    } else {
                        LOG_CONTEXT.set(captured);
                    }
                    task.run();
                } finally {
                    if (previous == null) {
                        LOG_CONTEXT.remove();
                    } else {
                        LOG_CONTEXT.set(previous);
                    }
                }
            });
        };
    }
}
