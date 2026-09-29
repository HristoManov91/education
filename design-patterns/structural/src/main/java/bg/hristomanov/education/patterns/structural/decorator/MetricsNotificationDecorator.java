package bg.hristomanov.education.patterns.structural.decorator;

import java.util.concurrent.atomic.AtomicInteger;

public class MetricsNotificationDecorator extends NotificationSenderDecorator {

    private final AtomicInteger sentCounter;

    public MetricsNotificationDecorator(NotificationSender delegate, AtomicInteger sentCounter) {
        super(delegate);
        this.sentCounter = sentCounter;
    }

    @Override
    public void send(Notification notification) {
        delegate.send(notification);
        sentCounter.incrementAndGet();
    }
}
