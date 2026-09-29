package bg.hristomanov.education.eventsourcing.snapshot;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "es_account_snapshots")
public class AccountSnapshotEntity {

    @Id
    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "stream_version", nullable = false)
    private long streamVersion;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountSnapshotEntity() {
    }

    public AccountSnapshotEntity(
            String accountId,
            long streamVersion,
            String ownerName,
            String currency,
            BigDecimal balance,
            Instant createdAt
    ) {
        this.accountId = accountId;
        this.streamVersion = streamVersion;
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public long getStreamVersion() {
        return streamVersion;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
