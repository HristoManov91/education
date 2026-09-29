package bg.hristomanov.education.cqrs.api;

import bg.hristomanov.education.cqrs.read.OrderSummaryDto;

public record CqrsState(
        long orderId,
        String writeStatus,
        long writeVersion,
        OrderSummaryDto readModel,
        long pendingProjectionRefreshes
) {
}
