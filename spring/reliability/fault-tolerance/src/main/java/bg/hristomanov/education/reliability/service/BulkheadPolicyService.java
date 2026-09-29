package bg.hristomanov.education.reliability.service;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Bulkhead ограничава броя едновременни calls към конкретен scarce resource.
 *
 * <p>Virtual threads правят blocking евтин за JVM-а, но не увеличават capacity
 * на DB connection pool, downstream service или rate-limited external API.</p>
 */
@Service
public class BulkheadPolicyService {

    private final ControllableDownstreamService downstream;
    private final Bulkhead bulkhead;

    public BulkheadPolicyService(
            ControllableDownstreamService downstream,
            Bulkhead bulkhead
    ) {
        this.downstream = downstream;
        this.bulkhead = bulkhead;
    }

    public DownstreamResponse call() {
        Supplier<DownstreamResponse> decorated =
                Bulkhead.decorateSupplier(
                        bulkhead,
                        downstream::call
                );

        return decorated.get();
    }
}
