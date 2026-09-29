package bg.hristomanov.education.patterns.structural.decorator;

/**
 * Decorator запазва същия interface и добавя behavior около delegate-а.
 */
public abstract class NotificationSenderDecorator implements NotificationSender {

    protected final NotificationSender delegate;

    protected NotificationSenderDecorator(NotificationSender delegate) {
        this.delegate = delegate;
    }
}
