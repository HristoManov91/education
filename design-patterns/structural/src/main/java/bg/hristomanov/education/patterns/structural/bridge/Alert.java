package bg.hristomanov.education.patterns.structural.bridge;

/**
 * Abstraction hierarchy в Bridge.
 *
 * <p>Alert type-ът и delivery transport-ът са две независими dimensions
 * (измерения на вариация). Вместо класове като EmailSecurityAlert,
 * SlackSecurityAlert, EmailOperationalAlert и т.н., abstraction-ът държи
 * implementation object чрез composition.</p>
 */
public abstract class Alert {

    protected final AlertTransport transport;

    protected Alert(AlertTransport transport) {
        this.transport = transport;
    }

    public abstract String notify(String recipient, String message);
}
