package bg.hristomanov.education.cqrs.write;

import bg.hristomanov.education.cqrs.projection.OrderProjectionWriter;
import bg.hristomanov.education.cqrs.projection.ProjectionRefreshRequest;
import bg.hristomanov.education.cqrs.projection.ProjectionRefreshRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Command side на CQRS.
 *
 * <p>Commands изразяват business intent и променят write model-а.
 * Този service не връща query-optimized DTO; връща само identity/acknowledgement.</p>
 */
@Service
public class OrderCommandService {

    private final OrderWriteRepository orderRepository;
    private final OrderProjectionWriter projectionWriter;
    private final ProjectionRefreshRequestRepository refreshRequestRepository;

    public OrderCommandService(
            OrderWriteRepository orderRepository,
            OrderProjectionWriter projectionWriter,
            ProjectionRefreshRequestRepository refreshRequestRepository
    ) {
        this.orderRepository = orderRepository;
        this.projectionWriter = projectionWriter;
        this.refreshRequestRepository = refreshRequestRepository;
    }

    @Transactional
    public long create(
            CreateOrderCommand command,
            ProjectionMode projectionMode
    ) {
        List<OrderLineEntity> lines = command.lines()
                .stream()
                .map(line ->
                        new OrderLineEntity(
                                line.sku(),
                                line.quantity(),
                                line.unitPrice()
                        )
                )
                .toList();

        OrderWriteEntity order = new OrderWriteEntity(
                command.reference(),
                command.customerId(),
                lines,
                Instant.now()
        );

        orderRepository.saveAndFlush(order);
        updateReadModel(order, projectionMode);

        return order.getId();
    }

    @Transactional
    public void markPaid(
            long orderId,
            ProjectionMode projectionMode
    ) {
        OrderWriteEntity order = required(orderId);

        order.markPaid();

        /*
         * Flush е умишлен в учебния пример: така @Version вече е increment-нат,
         * когато copy-ваме sourceVersion към read projection-а.
         */
        orderRepository.flush();

        updateReadModel(order, projectionMode);
    }

    private void updateReadModel(
            OrderWriteEntity order,
            ProjectionMode projectionMode
    ) {
        if (projectionMode == ProjectionMode.SYNCHRONOUS) {
            projectionWriter.project(order);
            return;
        }

        /*
         * Durable local hand-off. Това е малък CQRS projection queue,
         * не message broker. По-късно същата идея може да бъде реализирана
         * чрез Domain Event + Outbox + отделен read store.
         */
        refreshRequestRepository.save(
                new ProjectionRefreshRequest(
                        order.getId(),
                        Instant.now()
                )
        );
    }

    private OrderWriteEntity required(long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown order: " + orderId)
                );
    }
}
