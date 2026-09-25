package bg.hristomanov.education.loom.labs.joiner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;

/**
 * Custom Joiner, който изчаква всички subtasks и избира "най-добрия" успешен резултат
 * според подадения Comparator.
 *
 * <p>Това е различна concurrency policy от built-in
 * {@code anySuccessfulResultOrThrow()}: там оптимизираме за latency и приключваме
 * при първия успешен резултат. Тук съзнателно чакаме всички, защото качеството
 * на резултата е по-важно от минималната latency.</p>
 *
 * <p>Joiner-ът е generic нарочно. Business критерият "по-добър" идва отвън чрез
 * Comparator и не е скрит вътре в concurrency инфраструктурата.</p>
 *
 * <p>Важно: {@link #onComplete(StructuredTaskScope.Subtask)} може да бъде извикан
 * едновременно от няколко child threads. Затова mutable state-ът е thread-safe.</p>
 *
 * @param <T> типът на резултатите от subtasks
 */
public final class BestSuccessfulResultJoiner<T>
        implements StructuredTaskScope.Joiner<T, T> {

    private final Comparator<? super T> comparator;
    private final List<T> successfulResults =
            Collections.synchronizedList(new ArrayList<>());
    private final List<Throwable> failures =
            Collections.synchronizedList(new ArrayList<>());

    public BestSuccessfulResultJoiner(Comparator<? super T> comparator) {
        this.comparator = Objects.requireNonNull(comparator);
    }

    @Override
    public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
        /*
         * onComplete(...) се извиква от thread-а, изпълнил конкретната subtask.
         * Няколко tasks могат да приключат почти едновременно, затова тук не използваме
         * обикновен ArrayList без синхронизация.
         *
         * Връщаме false: тази policy НЕ short-circuit-ва. Искаме да видим резултата
         * на всяка provider задача, преди да изберем най-добрата.
         */
        switch (subtask.state()) {
            case SUCCESS -> successfulResults.add(subtask.get());
            case FAILED -> failures.add(subtask.exception());
            case UNAVAILABLE -> throw new IllegalStateException(
                    "onComplete must not receive an unavailable subtask");
        }

        return false;
    }

    @Override
    public T result() throws Throwable {
        /*
         * join() извиква result() след като scope-ът вече е изчакал всички subtasks
         * според тази policy. Ако имаме поне един успех, failures не правят цялата
         * операция неуспешна: избираме най-добрия успешен резултат.
         */
        synchronized (successfulResults) {
            if (!successfulResults.isEmpty()) {
                return successfulResults.stream()
                        .max(comparator)
                        .orElseThrow();
            }
        }

        /*
         * Ако нямаме нито успех, нито failure, значи scope-ът е бил празен.
         * Държим този случай отделен от "всички provider-и fail-наха".
         */
        synchronized (failures) {
            if (failures.isEmpty()) {
                throw new NoSuchElementException("No subtasks were completed");
            }

            AllSubtasksFailedException aggregateException =
                    new AllSubtasksFailedException(
                            "All " + failures.size() + " subtasks failed");

            /*
             * Suppressed exceptions пазят отделните причини. Така caller-ът получава
             * един outcome от join(), но диагностиката не губи отделните failures.
             */
            for (Throwable failure : failures) {
                aggregateException.addSuppressed(failure);
            }

            throw aggregateException;
        }
    }

    /**
     * Aggregate failure за случая, в който няма нито един успешен result.
     */
    public static final class AllSubtasksFailedException extends Exception {

        public AllSubtasksFailedException(String message) {
            super(message);
        }
    }
}
