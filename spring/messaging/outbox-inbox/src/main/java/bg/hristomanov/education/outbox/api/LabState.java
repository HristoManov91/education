package bg.hristomanov.education.outbox.api;

import bg.hristomanov.education.outbox.broker.BrokerMessage;

import java.util.List;

public record LabState(
        long orders,
        long outboxRows,
        long unpublishedOutboxRows,
        long processedMessages,
        long loyaltyAccounts,
        List<BrokerMessage> brokerMessages
) {
}
