package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.jfr.domain.LabResults.LockResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Създава monitor contention (няколко threads се конкурират за един и същ intrinsic lock).
 *
 * <p>Този пример е нарочно различен от CPU hotspot-а. Приложението може да има лоша latency,
 * без CPU да е 100%. Причината може да е, че threads чакат shared lock (споделено заключване).</p>
 *
 * <p>{@code Thread.sleep(...)} вътре в synchronized блока е учебна провокация: увеличаваме
 * времето, през което един thread държи monitor-а, за да получим ясно видими
 * {@code jdk.JavaMonitorEnter} събития в JFR.</p>
 */
@Service
public class LockContentionService {

    private static final int MIN_WORKERS = 2;
    private static final int MAX_WORKERS = 12;
    private static final int MIN_HOLD_MILLIS = 10;
    private static final int MAX_HOLD_MILLIS = 500;

    private final Object sharedMonitor = new Object();

    public LockResult createContention(int requestedWorkers, int requestedHoldMillis) {
        int workers = Math.max(MIN_WORKERS, Math.min(requestedWorkers, MAX_WORKERS));
        int holdMillis = Math.max(MIN_HOLD_MILLIS, Math.min(requestedHoldMillis, MAX_HOLD_MILLIS));

        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> waits = new ArrayList<>(workers);

        long scenarioStartedAt = System.nanoTime();

        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                Future<Long> future = executor.submit(
                        () -> waitForStartAndEnterMonitor(ready, start, holdMillis));
                waits.add(future);
            }

            /*
             * Първо чакаме всички worker threads да са готови. После ги освобождаваме наведнъж.
             * Това прави contention-а възпроизводим вместо да разчитаме на случайния scheduler timing.
             */
            ready.await();
            start.countDown();

            long totalWaitNanos = 0L;
            long maxWaitNanos = 0L;

            for (Future<Long> wait : waits) {
                long waitNanos = wait.get();
                totalWaitNanos += waitNanos;
                maxWaitNanos = Math.max(maxWaitNanos, waitNanos);
            }

            long durationMillis = nanosToMillis(System.nanoTime() - scenarioStartedAt);
            double averageWaitMillis = nanosToMillisAsDouble(totalWaitNanos) / workers;
            double maxWaitMillis = nanosToMillisAsDouble(maxWaitNanos);

            return new LockResult(
                    workers,
                    holdMillis,
                    durationMillis,
                    averageWaitMillis,
                    maxWaitMillis);
        } catch (InterruptedException e) {
            /*
             * InterruptedException изчиства interrupt status-а при хвърляне.
             * Възстановяваме го, за да не загубим cooperative cancellation signal-а
             * (сигнала, че текущата операция е поискана за прекратяване).
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock contention scenario was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Lock contention worker failed", e.getCause());
        }
    }

    private long waitForStartAndEnterMonitor(
            CountDownLatch ready,
            CountDownLatch start,
            int holdMillis) throws InterruptedException {

        ready.countDown();
        start.await();

        long waitStartedAt = System.nanoTime();

        synchronized (sharedMonitor) {
            long acquiredAt = System.nanoTime();

            /*
             * Това е BAD pattern в реално приложение: държим shared monitor докато чакаме.
             * Тук го правим умишлено, за да можем после да го диагностицираме с JFR.
             */
            Thread.sleep(holdMillis);

            return acquiredAt - waitStartedAt;
        }
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private double nanosToMillisAsDouble(long nanos) {
        return nanos / 1_000_000.0d;
    }
}
