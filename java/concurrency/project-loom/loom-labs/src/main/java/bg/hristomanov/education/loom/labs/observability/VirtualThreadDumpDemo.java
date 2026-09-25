package bg.hristomanov.education.loom.labs.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Създава много блокирани virtual threads и оставя кратък прозорец,
 * в който можем да ги наблюдаваме с JDK tooling.
 *
 * <p>Целта е да свържем теорията "можем да имаме много virtual threads"
 * с реален thread dump, а не само с логове от Thread.currentThread().</p>
 */
public final class VirtualThreadDumpDemo {

    private static final int DEFAULT_THREAD_COUNT = 2_000;
    private static final int DEFAULT_HOLD_SECONDS = 30;

    private VirtualThreadDumpDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCount = args.length > 0
                ? Integer.parseInt(args[0])
                : DEFAULT_THREAD_COUNT;

        int holdSeconds = args.length > 1
                ? Integer.parseInt(args[1])
                : DEFAULT_HOLD_SECONDS;

        CountDownLatch release = new CountDownLatch(1);
        List<Thread> virtualThreads = new ArrayList<>(threadCount);

        Thread.Builder.OfVirtual threadBuilder =
                Thread.ofVirtual().name("observed-vt-", 0);

        for (int index = 0; index < threadCount; index++) {
            Thread thread = threadBuilder.start(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            virtualThreads.add(thread);
        }

        long pid = ProcessHandle.current().pid();

        System.out.println("Started " + threadCount + " blocked virtual threads.");
        System.out.println("PID: " + pid);
        System.out.println();
        System.out.println("While this process is alive, run:");
        System.out.println(
                "jcmd " + pid
                        + " Thread.dump_to_file -format=json virtual-threads.json");
        System.out.println();
        System.out.println(
                "The demo will release the threads after "
                        + holdSeconds + " seconds.");

        try {
            Thread.sleep(holdSeconds * 1_000L);
        } finally {
            release.countDown();
        }

        for (Thread virtualThread : virtualThreads) {
            virtualThread.join();
        }

        System.out.println("All virtual threads completed.");
    }
}
