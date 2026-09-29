package bg.hristomanov.education.patterns.structural.bridge;

public interface AlertTransport {

    String send(String recipient, String subject, String body);
}
