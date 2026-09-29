package bg.hristomanov.education.patterns.structural.bridge;

public class OperationalAlert extends Alert {

    public OperationalAlert(AlertTransport transport) {
        super(transport);
    }

    @Override
    public String notify(String recipient, String message) {
        return transport.send(recipient, "Operational alert", message);
    }
}
