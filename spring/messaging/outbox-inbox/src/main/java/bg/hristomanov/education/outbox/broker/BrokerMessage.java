package bg.hristomanov.education.outbox.broker;

public record BrokerMessage(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload
) {
}
