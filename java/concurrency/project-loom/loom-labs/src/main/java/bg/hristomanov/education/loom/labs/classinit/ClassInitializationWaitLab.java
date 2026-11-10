package bg.hristomanov.education.loom.labs.classinit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Безопасна демонстрация на виртуални нишки, чакащи чужд class initializer.
 * Стартирай по веднъж в отделен JVM: Class.forName/инициализацията е еднократна.
 * Сравнява JDK25/26+ carrier behavior чрез JFR, без да assert-ва performance.
 */
public final class ClassInitializationWaitLab {

    private static volatile CountDownLatch initializerEntered;
    private static volatile CountDownLatch releaseInitializer;
    private static final AtomicInteger INITIALIZATIONS = new AtomicInteger();

    private ClassInitializationWaitLab() {
    }

    public static Result runOnce(int waitingTasks) throws Exception {
        if (waitingTasks < 1) {
            throw new IllegalArgumentException("waitingTasks must be positive");
        }
        initializerEntered = new CountDownLatch(1);
        releaseInitializer = new CountDownLatch(1);
        AtomicInteger initializerValue = new AtomicInteger(-1);
        Thread initializerThread = Thread.ofPlatform().name("class-initializer").start(
                () -> initializerValue.set(SlowClass.VALUE));

        try {
            if (!initializerEntered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Class initialization was not entered");
            }
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Integer>> waiting = new ArrayList<>();
                for (int index = 0; index < waitingTasks; index++) {
                    waiting.add(executor.submit(() -> SlowClass.VALUE));
                }
                // Възможност за class-init contention; не е timer/throughput oracle.
                Thread.sleep(120);
                releaseInitializer.countDown();

                int sum = 0;
                for (Future<Integer> future : waiting) {
                    sum += future.get(5, TimeUnit.SECONDS);
                }
                initializerThread.join(5_000);
                if (initializerThread.isAlive()) {
                    throw new IllegalStateException("Class initializer did not finish");
                }
                return new Result(initializerValue.get(), waiting.size(), sum,
                        INITIALIZATIONS.get());
            }
        } finally {
            // Дори при failure не оставяме platform initializer блокиран.
            releaseInitializer.countDown();
            initializerThread.join(5_000);
        }
    }

    public static void main(String[] args) throws Exception {
        int waiters = args.length == 0 ? 8 : Integer.parseInt(args[0]);
        Result result = runOnce(waiters);
        System.out.printf("INITIAL_VALUE=%d WAITERS=%d SUM=%d INIT_COUNT=%d%n",
                result.initialValue(), result.waiters(), result.sum(), result.initCount());
    }

    public record Result(int initialValue, int waiters, int sum, int initCount) {
    }

    private static final class SlowClass {
        // Не е compile-time constant: трябва действителна class initialization.
        private static final int VALUE = initialize();

        private static int initialize() {
            INITIALIZATIONS.incrementAndGet();
            initializerEntered.countDown();
            try {
                if (!releaseInitializer.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Initializer was never released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted class initialization", exception);
            }
            return 42;
        }
    }
}
