package bg.hristomanov.education.cqrs.write;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Само учебен inspector за tests/lab diagnostics.
 *
 * <p>Order lines са LAZY. Вместо да включваме OSIV или да правим association-а
 * EAGER, четем aggregate graph-а вътре в explicit read-only transaction.</p>
 */
@Service
@Transactional(readOnly = true)
public class OrderWriteInspectorService {

    private final OrderWriteRepository repository;

    public OrderWriteInspectorService(OrderWriteRepository repository) {
        this.repository = repository;
    }

    public OrderWriteSnapshot snapshot(long orderId) {
        OrderWriteEntity order = repository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown order: " + orderId)
                );

        return new OrderWriteSnapshot(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getLines().size(),
                order.getVersion()
        );
    }
}
