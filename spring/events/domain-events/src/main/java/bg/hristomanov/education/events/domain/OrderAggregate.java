package bg.hristomanov.education.events.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "domain_orders")
public class OrderAggregate
        extends AbstractAggregateRoot<OrderAggregate> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    protected OrderAggregate() {
    }

    public OrderAggregate(String reference) {
        this.reference = Objects.requireNonNull(reference);
        this.status = OrderStatus.NEW;
    }

    public void markPaid() {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Only NEW order can be paid");
        }

        status = OrderStatus.PAID;

        /*
         * Aggregate-ът регистрира business fact.
         *
         * Той не вика email service, Kafka producer или projection repository.
         * Това би coupling-нало domain behavior към reaction/infrastructure.
         */
        registerEvent(
                new OrderPaid(
                        id,
                        reference,
                        Instant.now()
                )
        );
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
