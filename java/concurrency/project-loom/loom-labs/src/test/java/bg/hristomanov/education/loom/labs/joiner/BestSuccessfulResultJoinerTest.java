package bg.hristomanov.education.loom.labs.joiner;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BestSuccessfulResultJoinerTest {

    @Test
    void selectsTheBestSuccessfulResultEvenWhenAnotherSubtaskFails()
            throws InterruptedException {

        BestSuccessfulResultJoiner<Integer> joiner =
                new BestSuccessfulResultJoiner<>(
                        Comparator.naturalOrder());

        try (StructuredTaskScope<Integer, Integer> scope =
                     StructuredTaskScope.open(joiner)) {

            scope.fork(() -> 70);
            scope.fork(() -> 95);
            scope.fork(() -> {
                throw new IllegalStateException("provider unavailable");
            });

            Integer result = scope.join();

            assertEquals(95, result);
        }
    }

    @Test
    void aggregatesIndividualFailuresWhenEverySubtaskFails() {
        BestSuccessfulResultJoiner<String> joiner =
                new BestSuccessfulResultJoiner<>(
                        Comparator.naturalOrder());

        StructuredTaskScope.FailedException failedException =
                assertThrows(
                        StructuredTaskScope.FailedException.class,
                        () -> {
                            try (StructuredTaskScope<String, String> scope =
                                         StructuredTaskScope.open(joiner)) {

                                scope.fork(() -> {
                                    throw new IllegalStateException("provider-a failed");
                                });

                                scope.fork(() -> {
                                    throw new IllegalArgumentException("provider-b failed");
                                });

                                scope.join();
                            }
                        });

        Throwable cause = failedException.getCause();

        assertInstanceOf(
                BestSuccessfulResultJoiner.AllSubtasksFailedException.class,
                cause);

        assertEquals(2, cause.getSuppressed().length);
    }
}
