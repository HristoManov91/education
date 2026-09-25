package bg.hristomanov.education.loom.labs.timeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeoutAndCancellationDemoTest {

    @Test
    void timeoutInterruptsCooperativeChildTask() throws InterruptedException {
        TimeoutAndCancellationDemo.TimeoutObservation observation =
                TimeoutAndCancellationDemo.runCooperative(
                        Duration.ofMillis(100),
                        Duration.ofSeconds(5));

        assertTrue(observation.timedOut());
        assertTrue(observation.childObservedInterrupt());
    }

    @Test
    void timeoutCannotForcefullyKillTaskThatIgnoresInterrupt()
            throws InterruptedException {

        TimeoutAndCancellationDemo.TimeoutObservation observation =
                TimeoutAndCancellationDemo.runIgnoringInterrupt(
                        Duration.ofMillis(60),
                        Duration.ofMillis(220));

        assertTrue(observation.timedOut());
        assertTrue(observation.ignoredInterruptCount() > 0);

        /*
         * Timeout-ът е 60 ms, но child task-ът игнорира interrupt и продължава.
         * Оставяме tolerance, за да не правим теста прекалено чувствителен към CI scheduling.
         */
        assertTrue(observation.elapsedMillis() >= 140);
    }
}
