package bg.hristomanov.education.loom.labs.deadline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Java 25 experiment: collect responses that finished within a shared time
 * budget, without making one slow/failed provider invalidate every result.
 *
 * Java 25 allUntil() does not return partial results on a configured
 * StructuredTaskScope timeout. A dedicated deadline subtask ends the scope
 * through the Joiner predicate instead. See VIRTUAL-THREADS-LABS.md.
 */
public final class PartialResultsUntilDeadlineDemo {

    private PartialResultsUntilDeadlineDemo() {
    }

    public record Provider(String name, Callable<String> request) {
        public Provider {
            Objects.requireNonNull(name);
            Objects.requireNonNull(request);
        }
    }

    public record Hit(String provider, String value) {
    }

    public record ProviderFailure(String provider, String exceptionType) {
    }

    public record PartialResults(
            List<Hit> hits, List<ProviderFailure> failures, boolean deadlineReached) {
        public PartialResults {
            hits = List.copyOf(hits);
            failures = List.copyOf(failures);
        }
    }

    private sealed interface Outcome permits Success, Failure, DeadlineReached {
    }

    private record Success(Hit hit, long elapsedNanos) implements Outcome {
    }

    private record Failure(ProviderFailure failure, long elapsedNanos) implements Outcome {
    }

    private record DeadlineReached() implements Outcome {
    }

    public static PartialResults collect(List<Provider> providers, Duration budget)
            throws InterruptedException {
        Objects.requireNonNull(providers);
        Objects.requireNonNull(budget);
        if (budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("Budget must be positive");
        }
        if (providers.isEmpty()) {
            return new PartialResults(List.of(), List.of(), false);
        }

        long budgetNanos = budget.toNanos();
        long startedAt = System.nanoTime();
        AtomicInteger providersFinished = new AtomicInteger();

        // onComplete may run on many virtual threads: the counter is atomic.
        StructuredTaskScope.Joiner<Outcome, Stream<StructuredTaskScope.Subtask<Outcome>>> joiner =
                StructuredTaskScope.Joiner.allUntil(subtask -> {
                    if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS
                            && subtask.get() instanceof DeadlineReached) {
                        return true;
                    }
                    // Return as soon as the last provider completes, even if it failed.
                    return providersFinished.incrementAndGet() == providers.size();
                });

        try (StructuredTaskScope<Outcome, Stream<StructuredTaskScope.Subtask<Outcome>>> scope =
                     StructuredTaskScope.open(joiner)) {

            // Fork deadline FIRST so instant provider completions cannot cancel
            // the scope before we have registered the time boundary.
            scope.fork(() -> {
                while (true) {
                    long remaining = budgetNanos - (System.nanoTime() - startedAt);
                    if (remaining <= 0) {
                        return new DeadlineReached();
                    }
                    TimeUnit.NANOSECONDS.sleep(remaining);
                }
            });

            for (Provider provider : providers) {
                scope.fork(() -> {
                    try {
                        String value = provider.request().call();
                        return new Success(
                                new Hit(provider.name(), value),
                                System.nanoTime() - startedAt);
                    } catch (InterruptedException e) {
                        // Cancellation is cooperative. Do not turn it into a
                        // normal business failure or swallow the interrupt.
                        throw e;
                    } catch (Exception e) {
                        return new Failure(
                                new ProviderFailure(
                                        provider.name(), e.getClass().getSimpleName()),
                                System.nanoTime() - startedAt);
                    }
                });
            }

            List<StructuredTaskScope.Subtask<Outcome>> subtasks = scope.join().toList();
            List<Hit> hits = new ArrayList<>();
            List<ProviderFailure> failures = new ArrayList<>();
            boolean deadlineReached = false;

            for (StructuredTaskScope.Subtask<Outcome> subtask : subtasks) {
                if (subtask.state() != StructuredTaskScope.Subtask.State.SUCCESS) {
                    // A cancelled task may be UNAVAILABLE (or report interruption).
                    continue;
                }

                Outcome outcome = subtask.get();
                if (outcome instanceof DeadlineReached) {
                    deadlineReached = true;
                } else if (outcome instanceof Success success
                        && success.elapsedNanos() <= budgetNanos) {
                    hits.add(success.hit());
                } else if (outcome instanceof Failure failure
                        && failure.elapsedNanos() <= budgetNanos) {
                    failures.add(failure.failure());
                }
            }

            return new PartialResults(hits, failures, deadlineReached);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        List<Provider> providers = List.of(
                new Provider("catalog", () -> {
                    Thread.sleep(30);
                    return "two matches";
                }),
                new Provider("cache", () -> {
                    Thread.sleep(80);
                    return "one match";
                }),
                new Provider("remote-partner", () -> {
                    Thread.sleep(2_000);
                    return "late match";
                }));

        PartialResults result = collect(providers, Duration.ofMillis(350));
        System.out.println("Deadline reached: " + result.deadlineReached());
        System.out.println("Results received in time: " + result.hits());
        System.out.println("Failed providers: " + result.failures());
    }
}
