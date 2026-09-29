package bg.hristomanov.education.reliability.config;

import bg.hristomanov.education.reliability.downstream.DownstreamTimeoutException;
import bg.hristomanov.education.reliability.downstream.PermanentDownstreamException;
import bg.hristomanov.education.reliability.downstream.TransientDownstreamException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class ReliabilityConfiguration {

    @Bean
    public Retry downstreamRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(
                        TransientDownstreamException.class,
                        DownstreamTimeoutException.class
                )
                .ignoreExceptions(
                        PermanentDownstreamException.class,
                        BulkheadFullException.class
                )
                .build();

        return Retry.of("downstream", config);
    }

    @Bean
    public TimeLimiter downstreamTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(75))
                .cancelRunningFuture(true)
                .build();

        return TimeLimiter.of("downstream", config);
    }

    @Bean
    public CircuitBreaker downstreamCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofMillis(60))
                .slowCallRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(
                        TransientDownstreamException.class,
                        DownstreamTimeoutException.class
                )
                .ignoreExceptions(
                        PermanentDownstreamException.class,
                        BulkheadFullException.class
                )
                .build();

        return CircuitBreaker.of("downstream", config);
    }

    @Bean
    public Bulkhead downstreamBulkhead() {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(2)
                .maxWaitDuration(Duration.ZERO)
                .build();

        return Bulkhead.of("downstream", config);
    }

    @Bean(destroyMethod = "close")
    public ExecutorService reliabilityExecutor() {
        /*
         * Virtual threads са подходящи за blocking I/O task execution,
         * но НЕ отменят нуждата от downstream concurrency limit.
         * Bulkhead-ът по-долу остава отделният scarce-resource guard.
         */
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
