package bg.hristomanov.education.eventsourcing.projection;

import java.math.BigDecimal;

public record AccountBalanceView(
        String accountId,
        String ownerName,
        String currency,
        BigDecimal balance,
        long sourceVersion
) {

    public static AccountBalanceView from(
            AccountBalanceProjection projection
    ) {
        return new AccountBalanceView(
                projection.getAccountId(),
                projection.getOwnerName(),
                projection.getCurrency(),
                projection.getBalance(),
                projection.getSourceVersion()
        );
    }
}
