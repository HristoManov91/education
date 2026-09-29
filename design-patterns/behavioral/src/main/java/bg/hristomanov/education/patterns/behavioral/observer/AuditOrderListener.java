package bg.hristomanov.education.patterns.behavioral.observer;

import java.util.List;

public class AuditOrderListener implements OrderEventListener {

    private final List<String> auditLog;

    public AuditOrderListener(List<String> auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void onOrderPlaced(OrderPlacedEvent event) {
        auditLog.add("ORDER_PLACED:" + event.orderId() + ":" + event.total());
    }
}
