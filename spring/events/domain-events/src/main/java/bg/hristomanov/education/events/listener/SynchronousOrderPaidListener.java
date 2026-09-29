package bg.hristomanov.education.events.listener;

import bg.hristomanov.education.events.domain.OrderPaid;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Plain @EventListener е synchronous по default.
 *
 * <p>Exception тук се връща към publisher call-а и може да причини rollback
 * на transaction-а, в която Spring Data repository публикува domain event-а.</p>
 */
@Component
public class SynchronousOrderPaidListener {

    private final EventObservationProbe probe;

    public SynchronousOrderPaidListener(EventObservationProbe probe) {
        this.probe = probe;
    }

    @EventListener
    @Order(100)
    public void on(OrderPaid event) {
        probe.recordSynchronous(event);

        if (probe.shouldFailSynchronousListener()) {
            throw new IllegalStateException(
                    "Simulated synchronous domain event listener failure"
            );
        }
    }
}
