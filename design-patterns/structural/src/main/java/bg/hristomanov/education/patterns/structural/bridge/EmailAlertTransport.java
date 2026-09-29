package bg.hristomanov.education.patterns.structural.bridge;

public class EmailAlertTransport implements AlertTransport {

    @Override
    public String send(String recipient, String subject, String body) {
        return "EMAIL|" + recipient + "|" + subject + "|" + body;
    }
}
