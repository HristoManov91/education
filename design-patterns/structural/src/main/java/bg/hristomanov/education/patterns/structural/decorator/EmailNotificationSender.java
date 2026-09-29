package bg.hristomanov.education.patterns.structural.decorator;

import java.util.List;

public class EmailNotificationSender implements NotificationSender {

    private final List<String> outbox;

    public EmailNotificationSender(List<String> outbox) {
        this.outbox = outbox;
    }

    @Override
    public void send(Notification notification) {
        outbox.add(notification.recipient() + ":" + notification.message());
    }
}
