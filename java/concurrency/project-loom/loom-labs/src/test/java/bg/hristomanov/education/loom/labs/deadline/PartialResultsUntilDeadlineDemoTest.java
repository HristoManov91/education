package bg.hristomanov.education.loom.labs.deadline;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialResultsUntilDeadlineDemoTest {

    @Test
    void returnsOnlyTimelyResultsAndCancelsSlowProvider() throws InterruptedException {
        AtomicBoolean slowProviderWasInterrupted = new AtomicBoolean();

        List<PartialResultsUntilDeadlineDemo.Provider> providers = List.of(
                new PartialResultsUntilDeadlineDemo.Provider("fast", () -> "ready"),
                new PartialResultsUntilDeadlineDemo.Provider("slow", () -> {
                    try {
                        Thread.sleep(5_000);
                        return "too late";
                    } catch (InterruptedException e) {
                        slowProviderWasInterrupted.set(true);
                        throw e;
                    }
                }));

        PartialResultsUntilDeadlineDemo.PartialResults result =
                PartialResultsUntilDeadlineDemo.collect(
                        providers, Duration.ofMillis(600));

        assertTrue(result.deadlineReached());
        assertEquals(List.of(
                new PartialResultsUntilDeadlineDemo.Hit("fast", "ready")), result.hits());
        assertTrue(result.failures().isEmpty());
        assertTrue(slowProviderWasInterrupted.get(),
                "The structured scope must interrupt the unfinished provider");
    }

    @Test
    void returnsEarlyWhenAllProvidersFinishInsteadOfWaitingForDeadline()
            throws InterruptedException {
        PartialResultsUntilDeadlineDemo.PartialResults result =
                PartialResultsUntilDeadlineDemo.collect(
                        List.of(
                                new PartialResultsUntilDeadlineDemo.Provider(
                                        "first", () -> "A"),
                                new PartialResultsUntilDeadlineDemo.Provider(
                                        "second", () -> "B")),
                        Duration.ofSeconds(5));

        assertFalse(result.deadlineReached());
        assertEquals(List.of(
                new PartialResultsUntilDeadlineDemo.Hit("first", "A"),
                new PartialResultsUntilDeadlineDemo.Hit("second", "B")), result.hits());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void providerFailureDoesNotDiscardIndependentSuccess() throws InterruptedException {
        PartialResultsUntilDeadlineDemo.PartialResults result =
                PartialResultsUntilDeadlineDemo.collect(
                        List.of(
                                new PartialResultsUntilDeadlineDemo.Provider(
                                        "broken", () -> {
                                            throw new IllegalStateException("upstream unavailable");
                                        }),
                                new PartialResultsUntilDeadlineDemo.Provider(
                                        "working", () -> "fresh")),
                        Duration.ofSeconds(5));

        assertFalse(result.deadlineReached());
        assertEquals(List.of(
                new PartialResultsUntilDeadlineDemo.Hit("working", "fresh")), result.hits());
        assertEquals(List.of(
                new PartialResultsUntilDeadlineDemo.ProviderFailure(
                        "broken", "IllegalStateException")), result.failures());
    }

    @Test
    void rejectsNonPositiveDeadline() {
        assertThrows(IllegalArgumentException.class, () ->
                PartialResultsUntilDeadlineDemo.collect(
                        List.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                PartialResultsUntilDeadlineDemo.collect(
                        List.of(), Duration.ofMillis(-1)));
    }
}
