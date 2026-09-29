package bg.hristomanov.education.reliability.service;

import bg.hristomanov.education.reliability.downstream.DownstreamTimeoutException;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Adapter около Resilience4j TimeLimiter за synchronous blocking caller.
 *
 * <p>TimeLimiter налага deadline върху Future. При timeout cancelRunningFuture
 * изпраща cancellation към Future-а, но реалният downstream код трябва да
 * кооперира с interruption/cancellation, за да спре действително работата си.</p>
 */
@Component
public class TimeoutExecutor {

    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public TimeoutExecutor(
            TimeLimiter timeLimiter,
            ExecutorService executor
    ) {
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    public <T> T execute(Supplier<T> supplier) {
        try {
            return timeLimiter.executeFutureSupplier(
                    () -> {
                        Future<T> future = executor.submit(supplier::get);
                        return future;
                    }
            );
        } catch (TimeoutException exception) {
            throw new DownstreamTimeoutException(
                    "Downstream exceeded configured time budget",
                    exception
            );
        } catch (DownstreamTimeoutException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Downstream execution failed", exception);
        }
    }
}
