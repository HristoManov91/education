package bg.hristomanov.education.events.listener;

import bg.hristomanov.education.events.domain.OrderPaid;
import bg.hristomanov.education.events.integration.OrderPaidIntegrationEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class EventObservationProbe {

    private final CopyOnWriteArrayList<OrderPaid> synchronous =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderPaid> afterCommit =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderPaid> afterRollback =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderPaidIntegrationEvent> integrationEvents =
            new CopyOnWriteArrayList<>();
    private final AtomicBoolean failSynchronousListener =
            new AtomicBoolean();

    public void recordSynchronous(OrderPaid event) {
        synchronous.add(event);
    }

    public void recordAfterCommit(OrderPaid event) {
        afterCommit.add(event);
    }

    public void recordAfterRollback(OrderPaid event) {
        afterRollback.add(event);
    }

    public void recordIntegrationEvent(OrderPaidIntegrationEvent event) {
        integrationEvents.add(event);
    }

    public List<OrderPaid> synchronousEvents() {
        return List.copyOf(synchronous);
    }

    public List<OrderPaid> afterCommitEvents() {
        return List.copyOf(afterCommit);
    }

    public List<OrderPaid> afterRollbackEvents() {
        return List.copyOf(afterRollback);
    }

    public List<OrderPaidIntegrationEvent> integrationEvents() {
        return List.copyOf(integrationEvents);
    }

    public void failSynchronousListener(boolean fail) {
        failSynchronousListener.set(fail);
    }

    public boolean shouldFailSynchronousListener() {
        return failSynchronousListener.get();
    }

    public void reset() {
        synchronous.clear();
        afterCommit.clear();
        afterRollback.clear();
        integrationEvents.clear();
        failSynchronousListener.set(false);
    }
}
