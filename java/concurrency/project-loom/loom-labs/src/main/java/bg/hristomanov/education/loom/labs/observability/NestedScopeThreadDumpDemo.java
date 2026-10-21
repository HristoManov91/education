package bg.hristomanov.education.loom.labs.observability;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Creates a real parent -> nested scope -> leaf task hierarchy that can be
 * inspected in a JDK 25 JSON thread dump. No mocked thread-dump structure.
 */
public final class NestedScopeThreadDumpDemo {

    private NestedScopeThreadDumpDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        int holdSeconds = args.length == 0 ? 60 : Integer.parseInt(args[0]);
        if (holdSeconds <= 0) {
            throw new IllegalArgumentException("Hold duration must be positive");
        }

        CountDownLatch allLeavesStarted = new CountDownLatch(3);
        CountDownLatch releaseLeaves = new CountDownLatch(1);
        AtomicReference<List<String>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread owner = Thread.ofVirtual().name("loom-dump-owner").start(() -> {
            try {
                result.set(runScenario(allLeavesStarted, releaseLeaves));
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        try {
            if (!allLeavesStarted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Nested scopes did not start all three leaf tasks");
            }

            long pid = ProcessHandle.current().pid();
            System.out.println("Nested scopes are blocked. PID: " + pid);
            System.out.println("Run in a second terminal:");
            System.out.println("jcmd " + pid
                    + " Thread.dump_to_file -format=json loom-structured.json");
            System.out.println("Keeping the scope tree alive for " + holdSeconds + " seconds.");
            Thread.sleep(TimeUnit.SECONDS.toMillis(holdSeconds));
        } finally {
            releaseLeaves.countDown();
            owner.join();
        }

        if (failure.get() != null) {
            throw new IllegalStateException("The structured scenario failed", failure.get());
        }
        System.out.println("Completed without orphaned subtasks: " + result.get());
    }

    /**
     * Testable core: the owner cannot return until all three blocking leaves
     * have been released and both structured scopes have joined and closed.
     */
    public static List<String> runScenario(
            CountDownLatch allLeavesStarted,
            CountDownLatch releaseLeaves) throws InterruptedException {

        Objects.requireNonNull(allLeavesStarted);
        Objects.requireNonNull(releaseLeaves);
        ThreadFactory outerThreads = Thread.ofVirtual().name("loom-outer-", 0).factory();

        try (StructuredTaskScope<Object, Void> outer = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
                config -> config.withName("order-summary-scope")
                        .withThreadFactory(outerThreads))) {

            StructuredTaskScope.Subtask<String> customer = outer.fork(() -> {
                ThreadFactory innerThreads =
                        Thread.ofVirtual().name("loom-customer-", 0).factory();

                try (StructuredTaskScope<Object, Void> inner = StructuredTaskScope.open(
                        StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
                        config -> config.withName("customer-details-scope")
                                .withThreadFactory(innerThreads))) {

                    StructuredTaskScope.Subtask<String> profile = inner.fork(
                            () -> awaitRelease("profile", allLeavesStarted, releaseLeaves));
                    StructuredTaskScope.Subtask<String> preferences = inner.fork(
                            () -> awaitRelease("preferences", allLeavesStarted, releaseLeaves));

                    inner.join();
                    return profile.get() + "+" + preferences.get();
                }
            });

            StructuredTaskScope.Subtask<String> order = outer.fork(
                    () -> awaitRelease("order", allLeavesStarted, releaseLeaves));

            outer.join();
            return List.of(customer.get(), order.get());
        }
    }

    private static String awaitRelease(
            String taskName, CountDownLatch started, CountDownLatch release)
            throws InterruptedException {
        started.countDown();
        release.await();
        return taskName;
    }
}
