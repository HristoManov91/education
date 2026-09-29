package bg.hristomanov.education.patterns.behavioral.observer;

import java.util.List;

public class EmailOrderListener implements OrderEventListener {

    private final List<String> sentEmails;

    public EmailOrderListener(List<String> sentEmails) {
        this.sentEmails = sentEmails;
    }

    @Override
    public void onOrderPlaced(OrderPlacedEvent event) {
        sentEmails.add(event.customerEmail() + ":order=" + event.orderId());
    }
}
