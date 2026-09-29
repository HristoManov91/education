package bg.hristomanov.education.patterns.structural.bridge;

public class SecurityAlert extends Alert {

    public SecurityAlert(AlertTransport transport) {
        super(transport);
    }

    @Override
    public String notify(String recipient, String message) {
        return transport.send(recipient, "SECURITY", "[HIGH] " + message);
    }
}
