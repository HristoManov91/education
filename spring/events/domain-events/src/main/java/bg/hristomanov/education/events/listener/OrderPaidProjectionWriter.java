package bg.hristomanov.education.events.listener;

import bg.hristomanov.education.events.domain.OrderPaid;
import bg.hristomanov.education.events.projection.OrderPaidProjection;
import bg.hristomanov.education.events.repository.OrderPaidProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * AFTER_COMMIT listener вече не трябва да разчита на original business
 * transaction за нови writes. Затова projection write-ът има собствена
 * REQUIRES_NEW transaction.
 */
@Service
public class OrderPaidProjectionWriter {

    private final OrderPaidProjectionRepository repository;

    public OrderPaidProjectionWriter(
            OrderPaidProjectionRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(OrderPaid event) {
        repository.saveAndFlush(
                new OrderPaidProjection(
                        event.orderId(),
                        event.orderReference(),
                        Instant.now()
                )
        );
    }
}
