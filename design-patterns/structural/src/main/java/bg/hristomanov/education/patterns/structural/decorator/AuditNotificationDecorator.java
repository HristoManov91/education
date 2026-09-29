package bg.hristomanov.education.patterns.structural.decorator;

import java.util.List;

public class AuditNotificationDecorator extends NotificationSenderDecorator {

    private final List<String> auditLog;

    public AuditNotificationDecorator(NotificationSender delegate, List<String> auditLog) {
        super(delegate);
        this.auditLog = auditLog;
    }

    @Override
    public void send(Notification notification) {
        auditLog.add("before-send:" + notification.recipient());
        delegate.send(notification);
        auditLog.add("after-send:" + notification.recipient());
    }
}
