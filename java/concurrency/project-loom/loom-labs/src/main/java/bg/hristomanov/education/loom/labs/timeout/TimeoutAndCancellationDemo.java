package bg.hristomanov.education.loom.labs.timeout;

import java.time.Duration;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Демонстрира две свързани, но различни идеи:
 *
 * <ol>
 *     <li>global timeout принадлежи на цялата structured операция;</li>
 *     <li>cancellation е cooperative — timeout-ът interrupt-ва child задачите,
 *     но не може насила да "убие" код, който игнорира interruption.</li>
 * </ol>
 */
public final class TimeoutAndCancellationDemo {

    private TimeoutAndCancellationDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        TimeoutObservation cooperative = runCooperative(
                Duration.ofMillis(250),
                Duration.ofSeconds(5));

        System.out.println("=== Cooperative task ===");
        System.out.println("Timed out:            " + cooperative.timedOut());
        System.out.println("Child saw interrupt:  " + cooperative.childObservedInterrupt());
        System.out.println("Elapsed ms:           " + cooperative.elapsedMillis());

        TimeoutObservation ignoringInterrupt = runIgnoringInterrupt(
                Duration.ofMillis(150),
                Duration.ofMillis(600));

        System.out.println();
        System.out.println("=== Interrupt-ignoring task ===");
        System.out.println("Timed out:            " + ignoringInterrupt.timedOut());
        System.out.println("Interrupts ignored:   " + ignoringInterrupt.ignoredInterruptCount());
        System.out.println("Elapsed ms:           " + ignoringInterrupt.elapsedMillis());
        System.out.println();
        System.out.println(
                "Вторият elapsed е значително над timeout-а, защото close() изчаква child task-а.");
    }

    public static TimeoutObservation runCooperative(
            Duration timeout,
            Duration slowWorkDuration) throws InterruptedException {

        AtomicBoolean slowTaskObservedInterrupt = new AtomicBoolean(false);
        long startedNanos = System.nanoTime();
        boolean timedOut = false;

        StructuredTaskScope.Joiner<String, Void> joiner =
                StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow();

        try (StructuredTaskScope<String, Void> scope =
                     StructuredTaskScope.open(
                             joiner,
                             configuration -> configuration.withTimeout(timeout))) {

            scope.fork(() -> {
                Thread.sleep(40);
                return "fast";
            });

            scope.fork(() -> {
                try {
                    Thread.sleep(slowWorkDuration);
                    return "slow";
                } catch (InterruptedException e) {
                    /*
                     * Timeout-ът cancel-ва scope-а чрез interrupt.
                     * Task-ът уважава сигнала и приключва.
                     *
                     * Propagate-ваме InterruptedException директно, затова не го
                     * преобразуваме в unchecked exception и не възстановяваме flag-а.
                     */
                    slowTaskObservedInterrupt.set(true);
                    throw e;
                }
            });

            scope.join();

        } catch (StructuredTaskScope.TimeoutException e) {
            timedOut = true;
        }

        return new TimeoutObservation(
                timedOut,
                slowTaskObservedInterrupt.get(),
                0,
                elapsedMillis(startedNanos));
    }

    /**
     * BAD сценарий: child task-ът получава interrupt, но умишлено го игнорира и продължава.
     *
     * <p>StructuredTaskScope timeout-ът НЕ е Thread.stop(). Scope-ът се cancel-ва,
     * но try-with-resources трябва да затвори scope-а коректно и close() изчаква
     * child задачата да приключи. Затова реалното време може да е много над timeout-а.</p>
     */
    public static TimeoutObservation runIgnoringInterrupt(
            Duration timeout,
            Duration totalWorkDuration) throws InterruptedException {

        AtomicInteger ignoredInterrupts = new AtomicInteger();
        long startedNanos = System.nanoTime();
        long finishAtNanos = startedNanos + totalWorkDuration.toNanos();
        boolean timedOut = false;

        StructuredTaskScope.Joiner<String, Void> joiner =
                StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow();

        try (StructuredTaskScope<String, Void> scope =
                     StructuredTaskScope.open(
                             joiner,
                             configuration -> configuration.withTimeout(timeout))) {

            scope.fork(() -> {
                while (System.nanoTime() < finishAtNanos) {
                    long remainingNanos = finishAtNanos - System.nanoTime();
                    long sleepMillis = Math.max(
                            1,
                            Duration.ofNanos(remainingNanos).toMillis());

                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        /*
                         * Нарочно BAD: поглъщаме cancellation signal-а и продължаваме.
                         * Този код е тук само за да докаже защо timeout != hard kill.
                         */
                        ignoredInterrupts.incrementAndGet();
                    }
                }

                return "finished despite cancellation";
            });

            scope.join();

        } catch (StructuredTaskScope.TimeoutException e) {
            timedOut = true;
        }

        return new TimeoutObservation(
                timedOut,
                ignoredInterrupts.get() > 0,
                ignoredInterrupts.get(),
                elapsedMillis(startedNanos));
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    public record TimeoutObservation(
            boolean timedOut,
            boolean childObservedInterrupt,
            int ignoredInterruptCount,
            long elapsedMillis) {
    }
}
