package bg.hristomanov.education.loom.labs.joiner;

import java.util.Comparator;
import java.util.concurrent.StructuredTaskScope;

/**
 * Сравнява две коректни, но различни concurrency policies:
 *
 * <ol>
 *     <li>първият успешен резултат е достатъчен;</li>
 *     <li>чакаме всички и избираме най-добрия успешен резултат.</li>
 * </ol>
 *
 * <p>Ключовият урок е, че Joiner policy-то трябва да следва business requirement-а.
 * Не съществува универсално "най-добро" поведение.</p>
 */
public final class JoinerPolicyDemo {

    private static final Comparator<ProviderResult> BY_QUALITY =
            Comparator.comparingInt(ProviderResult::quality);

    private JoinerPolicyDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        ProviderResult firstSuccessful = loadFirstSuccessful();
        ProviderResult bestSuccessful = loadBestSuccessful();

        System.out.println("First successful result: " + firstSuccessful);
        System.out.println("Best successful result:  " + bestSuccessful);
    }

    private static ProviderResult loadFirstSuccessful() throws InterruptedException {
        /*
         * Latency-first policy:
         * щом имаме един успешен резултат, останалата работа вече не ни е нужна.
         */
        try (StructuredTaskScope<ProviderResult, ProviderResult> scope =
                     StructuredTaskScope.open(
                             StructuredTaskScope.Joiner
                                     .<ProviderResult>anySuccessfulResultOrThrow())) {

            forkProviders(scope);
            return scope.join();
        }
    }

    private static ProviderResult loadBestSuccessful() throws InterruptedException {
        /*
         * Quality-first policy:
         * custom Joiner-ът чака всички provider-и, игнорира отделните failures,
         * ако има поне един success, и после избира най-висок quality score.
         */
        BestSuccessfulResultJoiner<ProviderResult> joiner =
                new BestSuccessfulResultJoiner<>(BY_QUALITY);

        try (StructuredTaskScope<ProviderResult, ProviderResult> scope =
                     StructuredTaskScope.open(joiner)) {

            forkProviders(scope);
            return scope.join();
        }
    }

    private static void forkProviders(
            StructuredTaskScope<ProviderResult, ?> scope) {

        /*
         * fast-provider е най-бърз, но не е най-качествен.
         * quality-provider е по-бавен, но връща най-висок quality.
         * flaky-provider демонстрира, че единичен failure не трябва непременно
         * да проваля quality-first стратегията.
         */
        scope.fork(() -> callProvider("fast-provider", 70, 150, false));
        scope.fork(() -> callProvider("quality-provider", 95, 350, false));
        scope.fork(() -> callProvider("flaky-provider", 85, 220, true));
    }

    private static ProviderResult callProvider(
            String provider,
            int quality,
            long latencyMillis,
            boolean shouldFail) {

        try {
            Thread.sleep(latencyMillis);
        } catch (InterruptedException e) {
            /*
             * При first-successful policy по-бавните задачи могат да бъдат cancel-нати.
             * Thread.sleep(...) е interruptible, затова възстановяваме interrupt flag-а.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException(provider + " was interrupted", e);
        }

        if (shouldFail) {
            throw new IllegalStateException(provider + " is temporarily unavailable");
        }

        return new ProviderResult(provider, quality, latencyMillis);
    }

    public record ProviderResult(
            String provider,
            int quality,
            long simulatedLatencyMillis) {
    }
}
