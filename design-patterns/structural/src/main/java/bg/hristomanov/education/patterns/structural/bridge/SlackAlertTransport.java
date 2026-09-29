package bg.hristomanov.education.patterns.structural.bridge;

public class SlackAlertTransport implements AlertTransport {

    @Override
    public String send(String recipient, String subject, String body) {
        return "SLACK|" + recipient + "|" + subject + "|" + body;
    }
}
