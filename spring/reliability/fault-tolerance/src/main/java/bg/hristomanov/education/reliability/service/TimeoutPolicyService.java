package bg.hristomanov.education.reliability.service;

import bg.hristomanov.education.reliability.downstream.ControllableDownstreamService;
import bg.hristomanov.education.reliability.downstream.DownstreamResponse;
import org.springframework.stereotype.Service;

/**
 * Timeout ограничава колко дълго caller-ът е готов да чака.
 *
 * <p>Timeout НЕ означава, че downstream operation със сигурност е прекратена.
 * Cancellation трябва да бъде поддържана по цялата call chain.</p>
 */
@Service
public class TimeoutPolicyService {

    private final ControllableDownstreamService downstream;
    private final TimeoutExecutor timeoutExecutor;

    public TimeoutPolicyService(
            ControllableDownstreamService downstream,
            TimeoutExecutor timeoutExecutor
    ) {
        this.downstream = downstream;
        this.timeoutExecutor = timeoutExecutor;
    }

    public DownstreamResponse call() {
        return timeoutExecutor.execute(downstream::call);
    }
}
