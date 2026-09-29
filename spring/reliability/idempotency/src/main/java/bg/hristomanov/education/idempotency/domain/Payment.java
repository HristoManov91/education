package bg.hristomanov.education.idempotency.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "idempotent_payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_idempotency_key",
                        columnNames = "idempotency_key"
                )
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    protected Payment() {
    }

    public Payment(
            String idempotencyKey,
            String requestFingerprint,
            String customerId,
            BigDecimal amount,
            String currency
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.CREATED;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
