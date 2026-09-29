package bg.hristomanov.education.reliability.service;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import io.github.resilience4j.retry.Retry;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Retry решава transient failure, при който повторен attempt има реален шанс
 * да успее. Не трябва да retry-ваме permanent/business failures механично.
 */
@Service
public class RetryPolicyService {

    private final ControllableDownstreamService downstream;
    private final Retry retry;

    public RetryPolicyService(
            ControllableDownstreamService downstream,
            Retry retry
    ) {
        this.downstream = downstream;
        this.retry = retry;
    }

    public DownstreamResponse call() {
        Supplier<DownstreamResponse> decorated =
                Retry.decorateSupplier(retry, downstream::call);

        return decorated.get();
    }
}
