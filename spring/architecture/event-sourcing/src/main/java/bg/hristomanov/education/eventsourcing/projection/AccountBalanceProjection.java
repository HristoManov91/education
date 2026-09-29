package bg.hristomanov.education.eventsourcing.projection;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "es_account_balance_projection")
public class AccountBalanceProjection {

    @Id
    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt;

    protected AccountBalanceProjection() {
    }

    public AccountBalanceProjection(
            String accountId,
            String ownerName,
            String currency,
            long sourceVersion,
            Instant projectedAt
    ) {
        this.accountId = accountId;
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
        this.sourceVersion = sourceVersion;
        this.projectedAt = projectedAt;
    }

    public void deposit(
            BigDecimal amount,
            long sourceVersion,
            Instant projectedAt
    ) {
        this.balance = balance.add(amount);
        this.sourceVersion = sourceVersion;
        this.projectedAt = projectedAt;
    }

    public void withdraw(
            BigDecimal amount,
            long sourceVersion,
            Instant projectedAt
    ) {
        this.balance = balance.subtract(amount);
        this.sourceVersion = sourceVersion;
        this.projectedAt = projectedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public Instant getProjectedAt() {
        return projectedAt;
    }
}
